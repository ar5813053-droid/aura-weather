package com.aura.weather.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.aura.weather.handtracking.DetectedHand
import com.aura.weather.handtracking.HandTrackingResult
import kotlin.math.max
import kotlin.math.min

/**
 * Floating live-camera bubble over other apps.
 *
 * Does **not** open CameraX. Frames come from [HandDriveFrameHub], published by
 * [com.aura.weather.input.HandDriveTrackingService] from its existing analysis pipeline.
 */
class HandDriveCameraOverlayService : Service() {

    companion object {
        private const val TAG = "HandDriveOverlay"

        const val ACTION_SHOW = "com.aura.weather.action.SHOW_CAMERA_OVERLAY"
        const val ACTION_HIDE = "com.aura.weather.action.HIDE_CAMERA_OVERLAY"

        @Volatile
        var isShowing: Boolean = false
            private set

        fun show(context: Context) {
            val i = Intent(context, HandDriveCameraOverlayService::class.java).setAction(ACTION_SHOW)
            // Not a foreground service — tracking FGS already holds the camera.
            context.startService(i)
        }

        fun hide(context: Context) {
            val i = Intent(context, HandDriveCameraOverlayService::class.java).setAction(ACTION_HIDE)
            try {
                context.startService(i)
            } catch (_: Throwable) {
                // Service may not be running.
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var previewView: ImageView? = null
    private var skeletonView: SkeletonView? = null
    private var statusView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var expanded = false
    private val collapsedSizeDp = 120
    private val expandedSizeDp = 240

    private val frameListener: (HandDriveFrameHub.Snapshot) -> Unit = { snap ->
        mainHandler.post { applySnapshot(snap) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW, null -> {
                // Overlay is not a long-running FGS by itself; tracking service owns the camera FGS.
                // startService is enough. If started via startForegroundService, we must promote briefly
                // on API 26+ — but MainActivity/TrackingService should use startService for SHOW when
                // tracking FGS is already up. Use safe path:
                showOverlay()
                return START_STICKY
            }
            else -> {
                showOverlay()
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (rootView != null) {
            isShowing = true
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val sizePx = (collapsedSizeDp * density).toInt()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC10141C"))
            elevation = 12f * density
        }

        val preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Color.BLACK)
        }
        val skeleton = SkeletonView(this)
        val status = TextView(this).apply {
            text = "HandDrive"
            setTextColor(Color.parseColor("#00E5C7"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(8, 4, 8, 4)
            setBackgroundColor(Color.parseColor("#99000000"))
        }

        root.addView(
            preview,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            skeleton,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.TOP or Gravity.START }
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }

        setupTouch(root, params)

        try {
            windowManager?.addView(root, params)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to add overlay (permission?): ${t.message}")
            stopSelf()
            return
        }

        rootView = root
        previewView = preview
        skeletonView = skeleton
        statusView = status
        layoutParams = params
        expanded = false
        isShowing = true

        HandDriveFrameHub.addListener(frameListener)
        applySnapshot(HandDriveFrameHub.snapshot())
        Log.i(TAG, "Camera bubble shown")
    }

    private fun removeOverlay() {
        HandDriveFrameHub.removeListener(frameListener)
        val root = rootView
        val wm = windowManager
        if (root != null && wm != null) {
            try {
                wm.removeView(root)
            } catch (_: Throwable) {
            }
        }
        rootView = null
        previewView = null
        skeletonView = null
        statusView = null
        layoutParams = null
        isShowing = false
        Log.i(TAG, "Camera bubble hidden")
    }

    private fun applySnapshot(snap: HandDriveFrameHub.Snapshot) {
        val bmp = snap.bitmap
        if (bmp != null && !bmp.isRecycled) {
            previewView?.setImageBitmap(bmp)
        }
        skeletonView?.update(snap.hands)
        val hands = snap.hands?.hands?.size ?: 0
        val steer = if (snap.steeringEnabled) "STEER ON" else "PREVIEW"
        statusView?.text = "HandDrive · $hands hand(s) · $steer"
    }

    private fun setupTouch(root: FrameLayout, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var paramX = 0
        var paramY = 0
        var moved = false
        val touchSlop = 12f * resources.displayMetrics.density

        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    paramX = params.x
                    paramY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        moved = true
                    }
                    params.x = paramX + dx.toInt()
                    params.y = paramY + dy.toInt()
                    try {
                        windowManager?.updateViewLayout(root, params)
                    } catch (_: Throwable) {
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        toggleExpanded()
                    }
                    true
                }
                else -> false
            }
        }

        root.setOnLongClickListener {
            hide(applicationContext)
            true
        }
    }

    private fun toggleExpanded() {
        val params = layoutParams ?: return
        val root = rootView ?: return
        val density = resources.displayMetrics.density
        expanded = !expanded
        val size = ((if (expanded) expandedSizeDp else collapsedSizeDp) * density).toInt()
        params.width = size
        params.height = size
        try {
            windowManager?.updateViewLayout(root, params)
        } catch (_: Throwable) {
        }
    }

    /** Draws MediaPipe hand skeletons on top of the preview. */
    private class SkeletonView(context: Context) : View(context) {
        private var result: HandTrackingResult? = null
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#00E5C7")
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7A29")
            style = Paint.Style.FILL
        }

        private val connections = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 17 to 18, 18 to 19, 19 to 20,
            0 to 17
        )

        fun update(hands: HandTrackingResult?) {
            result = hands
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val hands = result?.hands ?: return
            val w = width.toFloat().coerceAtLeast(1f)
            val h = height.toFloat().coerceAtLeast(1f)
            for (hand in hands) {
                drawHand(canvas, hand, w, h)
            }
        }

        private fun drawHand(canvas: Canvas, hand: DetectedHand, w: Float, h: Float) {
            val pts = hand.landmarks
            if (pts.size < 21) return
            for ((a, b) in connections) {
                val pa = pts[a]
                val pb = pts[b]
                canvas.drawLine(pa.x * w, pa.y * h, pb.x * w, pb.y * h, linePaint)
            }
            for (p in pts) {
                canvas.drawCircle(p.x * w, p.y * h, 4f, pointPaint)
            }
        }
    }
}
