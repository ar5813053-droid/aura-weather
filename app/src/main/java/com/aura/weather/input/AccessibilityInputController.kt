package com.aura.weather.input

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * [InputController] that maps [SteeringInputState.steeringPercent] to short
 * horizontal [AccessibilityService] gestures via [HandDriveAccessibilityService].
 *
 * Behaviour (prototype):
 * - steeringPercent &lt; 0 → stroke toward the left steering zone
 * - steeringPercent &gt; 0 → stroke toward the right steering zone
 * - near 0 (dead zone) → no gesture
 * - updates are **throttled** so MediaPipe’s high frame rate does not spam
 *   [dispatchGesture]
 * - if the accessibility service is not enabled / connected, updates are
 *   ignored without crashing
 *
 * This does **not** guarantee control of any specific racing game. Many titles
 * use custom touch regions or ignore short swipes; calibration will be needed
 * per game in a later phase.
 *
 * Thread-safety: all mutable fields are atomic / volatile-friendly; safe to
 * call [updateSteering] from the MediaPipe callback thread.
 *
 * @param context used only to read default display size (not retained as Activity)
 * @param deadZonePercent |percent| below this → neutral (no gesture)
 * @param minUpdateIntervalMs minimum time between dispatched gestures
 * @param gestureDurationMs length of each stroke
 * @param maxStrokeFraction max horizontal travel as a fraction of screen width
 * @param zoneYFraction vertical position of the stroke (0 = top, 1 = bottom)
 */
class AccessibilityInputController(
    context: Context,
    private val deadZonePercent: Float = 8f,
    private val minUpdateIntervalMs: Long = 60L,
    private val gestureDurationMs: Long = 48L,
    private val maxStrokeFraction: Float = 0.22f,
    private val zoneYFraction: Float = 0.55f
) : InputController {

    companion object {
        private const val TAG = "HandDriveInputA11y"
    }

    private val appContext = context.applicationContext
    private val active = AtomicBoolean(false)
    private val lastStateRef = AtomicReference(SteeringInputState.IDLE)
    private val lastGestureUptimeMs = AtomicReference(0L)

    // Cached screen size; refreshed on start() in case of rotation.
    @Volatile private var screenWidthPx: Int = 1080
    @Volatile private var screenHeightPx: Int = 1920

    override val isActive: Boolean
        get() = active.get()

    override val lastState: SteeringInputState
        get() = lastStateRef.get()

    override fun start() {
        refreshDisplaySize()
        active.set(true)
        Log.i(
            TAG,
            "AccessibilityInputController started " +
                "(serviceConnected=${HandDriveAccessibilityService.isConnected()}, " +
                "display=${screenWidthPx}x${screenHeightPx})"
        )
    }

    override fun stop() {
        active.set(false)
        HandDriveAccessibilityService.instance?.cancelActiveGestures()
        reset()
        Log.i(TAG, "AccessibilityInputController stopped")
    }

    override fun reset() {
        lastStateRef.set(SteeringInputState.IDLE)
        lastGestureUptimeMs.set(0L)
    }

    override fun updateSteering(state: SteeringInputState) {
        lastStateRef.set(state)
        if (!active.get()) return

        val service = HandDriveAccessibilityService.instance
        if (service == null) {
            // Service not enabled in system settings — fail soft.
            return
        }

        val percent = state.steeringPercent.coerceIn(-100f, 100f)
        if (abs(percent) < deadZonePercent) {
            return
        }

        val now = System.currentTimeMillis()
        val last = lastGestureUptimeMs.get()
        if (now - last < minUpdateIntervalMs) {
            return
        }
        if (!lastGestureUptimeMs.compareAndSet(last, now)) {
            return // another thread won the throttle slot
        }

        val w = screenWidthPx.toFloat().coerceAtLeast(1f)
        val h = screenHeightPx.toFloat().coerceAtLeast(1f)
        val centerX = w * 0.5f
        val y = h * zoneYFraction
        // Stroke length scales with |percent|; direction by sign.
        val maxTravel = w * maxStrokeFraction
        val travel = (abs(percent) / 100f) * maxTravel
        val startX: Float
        val endX: Float
        if (percent > 0f) {
            // Right: center → right
            startX = centerX
            endX = (centerX + travel).coerceAtMost(w - 1f)
        } else {
            // Left: center → left
            startX = centerX
            endX = (centerX - travel).coerceAtLeast(0f)
        }

        try {
            service.dispatchSteeringGesture(
                startX = startX,
                endX = endX,
                y = y,
                durationMs = gestureDurationMs
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Gesture dispatch error (ignored): ${t.message}")
        }
    }

    private fun refreshDisplaySize() {
        try {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            if (metrics.widthPixels > 0) screenWidthPx = metrics.widthPixels
            if (metrics.heightPixels > 0) screenHeightPx = metrics.heightPixels
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read display size: ${t.message}")
        }
    }
}
