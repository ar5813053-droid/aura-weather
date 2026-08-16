package com.aura.weather.input

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.aura.weather.MainActivity
import com.aura.weather.handtracking.HandTracker
import com.aura.weather.overlay.HandDriveCameraOverlayService
import com.aura.weather.overlay.HandDriveFrameHub
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that owns CameraX + MediaPipe hand tracking independently
 * of [MainActivity]'s lifecycle.
 *
 * Why this exists:
 * MainActivity binds CameraX with [ProcessCameraProvider.bindToLifecycle] to the
 * Activity. When the user leaves/minimizes HandDrive, the Activity goes through
 * ON_PAUSE / ON_STOP and CameraX **unbinds analysis**, so hand X never updates
 * and [HandDriveAccessibilityService.updateSteeringDrag] is never called.
 *
 * This service keeps the camera/analysis pipeline alive while the notification
 * is showing, so START STEERING continues to feed system-wide gestures.
 */
class HandDriveTrackingService : Service(), LifecycleOwner {

    companion object {
        private const val TAG = "HandDriveInput"
        private const val CHANNEL_ID = "handdrive_tracking"
        private const val NOTIFICATION_ID = 42

        const val ACTION_START = "com.aura.weather.action.START_TRACKING_SERVICE"
        const val ACTION_STOP = "com.aura.weather.action.STOP_TRACKING_SERVICE"
        const val ACTION_ENABLE_STEERING = "com.aura.weather.action.ENABLE_STEERING"
        const val ACTION_DISABLE_STEERING = "com.aura.weather.action.DISABLE_STEERING"

        @Volatile var isServiceRunning: Boolean = false
            private set

        @Volatile var isSteeringEnabled: Boolean = false
            private set

        @Volatile var lastHandX: Float = Float.NaN
            private set

        @Volatile var lastSteering: Float = 0f
            private set

        @Volatile var lastHandsDetected: Int = 0
            private set

        fun start(context: Context) {
            val i = Intent(context, HandDriveTrackingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, HandDriveTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }

        fun enableSteering(context: Context) {
            val i = Intent(context, HandDriveTrackingService::class.java).setAction(ACTION_ENABLE_STEERING)
            ContextCompat.startForegroundService(context, i)
        }

        fun disableSteering(context: Context) {
            val i = Intent(context, HandDriveTrackingService::class.java).setAction(ACTION_DISABLE_STEERING)
            context.startService(i)
        }

        fun showCameraBubble(context: Context) {
            // Ensure tracking pipeline is up so frames are published.
            start(context)
            HandDriveCameraOverlayService.show(context)
        }

        fun hideCameraBubble(context: Context) {
            HandDriveCameraOverlayService.hide(context)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var handTracker: HandTracker? = null
    private val mapper = HandXSteeringMapper()
    private val steeringFlag = AtomicBoolean(false)
    private var lastOverlayPublishMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        createNotificationChannel()
        Log.i(TAG, "HandDriveTrackingService onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISABLE_STEERING -> {
                setSteeringEnabled(false)
                return START_STICKY
            }
            ACTION_ENABLE_STEERING -> {
                ensureForeground()
                startCameraIfNeeded()
                setSteeringEnabled(true)
                return START_STICKY
            }
            ACTION_START, null -> {
                ensureForeground()
                startCameraIfNeeded()
                return START_STICKY
            }
            else -> {
                ensureForeground()
                startCameraIfNeeded()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopEverything()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        isServiceRunning = false
        Log.i(TAG, "HandDriveTrackingService onDestroy")
        super.onDestroy()
    }

    private fun ensureForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isServiceRunning = true
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
            }
            if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
        }
    }

    private fun setSteeringEnabled(enabled: Boolean) {
        steeringFlag.set(enabled)
        isSteeringEnabled = enabled
        if (enabled) {
            mapper.reset()
            HandDriveFrameHub.steeringEnabled = true
            Log.i(TAG, "STEERING_STARTED")
        } else {
            HandDriveAccessibilityService.instance?.endSteeringDrag()
            mapper.reset()
            lastSteering = 0f
            HandDriveFrameHub.steeringEnabled = false
            Log.i(TAG, "STEERING_STOPPED")
        }
        // Refresh notification text
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun startCameraIfNeeded() {
        if (handTracker != null && cameraProvider != null) return

        handTracker = HandTracker(
            context = applicationContext,
            maxHands = 2,
            onResult = { result -> onHands(result) },
            onError = { msg -> Log.w(TAG, "HandTracker error: $msg") }
        )

        cameraExecutor.execute {
            try {
                handTracker?.setup()
            } catch (t: Throwable) {
                Log.e(TAG, "HandTracker setup failed: ${t.message}")
            }
        }

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                bindCamera(provider)
            } catch (t: Throwable) {
                Log.e(TAG, "Camera provider failed: ${t.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera(provider: ProcessCameraProvider) {
        try {
            provider.unbindAll()
            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { useCase ->
                    useCase.setAnalyzer(cameraExecutor) { imageProxy ->
                        val tracker = handTracker
                        // Share a downscaled frame with the overlay bubble (same pipeline, no 2nd camera).
                        maybePublishOverlayFrame(imageProxy)
                        if (tracker != null && tracker.isReady) {
                            tracker.detect(imageProxy, isFrontCamera = true)
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )
            Log.i(TAG, "Camera bound to HandDriveTrackingService lifecycle")
        } catch (t: Throwable) {
            Log.e(TAG, "bindCamera failed: ${t.message}")
        }
    }

    private fun onHands(result: com.aura.weather.handtracking.HandTrackingResult) {
        lastHandsDetected = result.hands.size
        val sorted = result.hands.sortedBy { it.wrist.x }
        val handX: Float? = when {
            sorted.isEmpty() -> null
            sorted.size == 1 -> sorted[0].wrist.x
            else -> (sorted[0].wrist.x + sorted[1].wrist.x) * 0.5f
        }

        val mapped = mapper.process(handX)
        lastHandX = mapped.handX
        lastSteering = mapped.steering
        HandDriveFrameHub.publishHands(result)

        if (!steeringFlag.get()) return

        if (handX == null) {
            HandDriveAccessibilityService.instance?.endSteeringDrag()
            Log.d(TAG, "HAND_X=lost STEERING_VALUE=0")
            return
        }

        Log.d(TAG, "HAND_X=${"%.3f".format(handX)} STEERING_VALUE=${"%.1f".format(mapped.steering)}")
        val service = HandDriveAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "DISPATCH_GESTURE=false (accessibility not connected)")
            return
        }
        service.updateSteeringDrag(mapped.steering)
        // updateSteeringDrag logs/returns internally; mark intent
        Log.d(TAG, "DISPATCH_GESTURE=true")
    }

    private fun stopEverything() {
        setSteeringEnabled(false)
        try {
            cameraProvider?.unbindAll()
        } catch (_: Throwable) {
        }
        cameraProvider = null
        try {
            handTracker?.close()
        } catch (_: Throwable) {
        }
        handTracker = null
        isServiceRunning = false
        HandDriveFrameHub.clear()
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED) &&
            lifecycleRegistry.currentState != Lifecycle.State.DESTROYED
        ) {
            try {
                lifecycleRegistry.currentState = Lifecycle.State.CREATED
            } catch (_: Throwable) {
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "HandDrive tracking",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps camera hand tracking running while steering in other apps"
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopSteering = PendingIntent.getService(
            this,
            1,
            Intent(this, HandDriveTrackingService::class.java).setAction(ACTION_DISABLE_STEERING),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (steeringFlag.get()) "HandDrive steering active" else "HandDrive tracking active"
        val text = if (steeringFlag.get()) {
            "System-wide steering on — open another app to test"
        } else {
            "Camera tracking running in background"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(open)
            .addAction(0, "Stop steering", stopSteering)
            .setOngoing(true)
            .build()
    }
}
