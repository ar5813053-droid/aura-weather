package com.aura.weather.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

/**
 * AccessibilityService gesture endpoint for HandDrive.
 *
 * - [performTestSwipe] / LEFT / RIGHT / TAP: discrete diagnostic gestures
 * - [updateSteeringDrag] / [endSteeringDrag]: sequential short drag segments
 *   that approximate continuous steering (see class KDoc on limitation)
 *
 * Limitation: Android cannot mutate an already-dispatched [GestureDescription].
 * Steering is therefore a sequence of short horizontal strokes (optionally
 * chained with willContinue on API 26+), not one infinite finger-down gesture.
 */
class HandDriveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HandDriveInput"

        private val instanceRef = AtomicReference<HandDriveAccessibilityService?>(null)

        val instance: HandDriveAccessibilityService?
            get() = instanceRef.get()

        fun isConnected(): Boolean = instanceRef.get() != null

        fun testLeftSwipe(): Boolean =
            instance?.dispatchTestLeftSwipe() ?: logNotConnected("TEST LEFT SWIPE")

        fun testRightSwipe(): Boolean =
            instance?.dispatchTestRightSwipe() ?: logNotConnected("TEST RIGHT SWIPE")

        fun testCenterTap(): Boolean =
            instance?.dispatchTestCenterTap() ?: logNotConnected("TEST CENTER TAP")

        fun testRealSwipe(): Boolean = performTestSwipe()

        fun performTestSwipe(): Boolean =
            instance?.performTestSwipe() ?: logNotConnected("TEST REAL SWIPE")

        private fun logNotConnected(label: String): Boolean {
            Log.w(TAG, "$label rejected — HandDriveAccessibilityService NOT CONNECTED")
            return false
        }
    }

    private val gestureInFlight = AtomicBoolean(false)

    // Virtual steering finger (sequential segments — not a true continuous hold)
    private val dragActive = AtomicBoolean(false)
    @Volatile private var lastDragX: Float = 0f
    @Volatile private var lastDragY: Float = 0f
    @Volatile private var lastDragUptimeMs: Long = 0L
    private var continuingStroke: GestureDescription.StrokeDescription? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Gesture dispatch only.
    }

    override fun onInterrupt() {
        gestureInFlight.set(false)
        dragActive.set(false)
        continuingStroke = null
    }

    override fun onDestroy() {
        instanceRef.compareAndSet(this, null)
        gestureInFlight.set(false)
        dragActive.set(false)
        continuingStroke = null
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Discrete steering helper (legacy short strokes)
    // ------------------------------------------------------------------

    fun dispatchSteeringGesture(
        startX: Float,
        endX: Float,
        y: Float,
        durationMs: Long
    ): Boolean {
        return dispatchStroke(
            startX = startX,
            endX = endX,
            startY = y,
            endY = y,
            durationMs = durationMs.coerceIn(16L, 500L),
            force = false,
            label = null
        )
    }

    fun cancelActiveGestures() {
        gestureInFlight.set(false)
        endSteeringDrag()
    }

    // ------------------------------------------------------------------
    // Virtual steering finger (hand X → sequential drag segments)
    // ------------------------------------------------------------------

    /**
     * Map steering percent [-100, +100] to a horizontal drag target and
     * dispatch a short segment from the previous position.
     *
     * Call on each processed tracking frame while virtual steering is enabled.
     * Throttled internally (~50 ms) so MediaPipe rate does not flood gestures.
     */
    fun updateSteeringDrag(steeringPercent: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        Log.d(TAG, "UPDATE_STEERING_DRAG in a11y percent=$steeringPercent")

        val (w, h) = screenSize()
        val y = h * 0.55f
        val centerX = w * 0.5f
        val travel = w * 0.35f
        val percent = steeringPercent.coerceIn(-100f, 100f)
        val targetX = (centerX + (percent / 100f) * travel).coerceIn(0f, w - 1f)

        val now = System.currentTimeMillis()
        if (dragActive.get() && now - lastDragUptimeMs < 50L) {
            return
        }

        if (!dragActive.get()) {
            // Touch-down style: begin a short stroke at target (willContinue on API 26+).
            lastDragX = targetX
            lastDragY = y
            val started = dispatchDragSegment(
                fromX = targetX,
                toX = targetX,
                y = y,
                durationMs = 40L,
                willContinue = true,
                isStart = true
            )
            if (started) {
                dragActive.set(true)
                lastDragUptimeMs = now
            }
            return
        }

        if (abs(targetX - lastDragX) < 2f) {
            return // negligible movement
        }

        val fromX = lastDragX
        val moved = dispatchDragSegment(
            fromX = fromX,
            toX = targetX,
            y = y,
            durationMs = 45L,
            willContinue = true,
            isStart = false
        )
        if (moved) {
            lastDragX = targetX
            lastDragY = y
            lastDragUptimeMs = now
        }
    }

    /**
     * Release the virtual finger: final segment with willContinue=false, or
     * clear state if nothing is active.
     */
    fun endSteeringDrag() {
        if (!dragActive.getAndSet(false)) {
            continuingStroke = null
            return
        }
        val x = lastDragX
        val y = lastDragY
        continuingStroke = null
        // Small lift: final non-continuing stroke at last position.
        dispatchDragSegment(
            fromX = x,
            toX = x,
            y = y,
            durationMs = 30L,
            willContinue = false,
            isStart = false
        )
        Log.i(TAG, "steering drag ended at x=$x")
    }

    private fun dispatchDragSegment(
        fromX: Float,
        toX: Float,
        y: Float,
        durationMs: Long,
        willContinue: Boolean,
        isStart: Boolean
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        // Allow overlapping steering segments by force-clearing the simple lock.
        gestureInFlight.set(false)

        val path = Path().apply {
            moveTo(fromX, y)
            lineTo(toX, y)
        }
        val duration = durationMs.coerceIn(16L, 200L)

        val stroke: GestureDescription.StrokeDescription = if (
            !isStart &&
            willContinue &&
            continuingStroke != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            try {
                continuingStroke!!.continueStroke(path, 0L, duration, willContinue)
            } catch (t: Throwable) {
                Log.w(TAG, "continueStroke failed, new stroke: ${t.message}")
                GestureDescription.StrokeDescription(path, 0L, duration, willContinue)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GestureDescription.StrokeDescription(path, 0L, duration, willContinue)
        } else {
            // API 24–25: independent short strokes (documented limitation).
            GestureDescription.StrokeDescription(path, 0L, duration)
        }

        if (willContinue && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            continuingStroke = stroke
        } else {
            continuingStroke = null
        }

        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return try {
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        Log.d(TAG, "DISPATCH_RESULT=completed")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        if (!willContinue) {
                            continuingStroke = null
                        }
                        Log.w(TAG, "DISPATCH_RESULT=cancelled")
                    }
                },
                null
            )
            Log.i(TAG, "DISPATCH_GESTURE=$accepted DISPATCH_RESULT=submitted")
            if (!accepted) {
                gestureInFlight.set(false)
            }
            accepted
        } catch (t: Throwable) {
            gestureInFlight.set(false)
            Log.w(TAG, "drag segment failed: ${t.message}")
            false
        }
    }

    // ------------------------------------------------------------------
    // Temporary diagnostic test gestures (work with tracking OFF)
    // ------------------------------------------------------------------

    fun dispatchTestLeftSwipe(): Boolean {
        val (w, h) = screenSize()
        val y = h * 0.5f
        return dispatchStroke(w * 0.70f, w * 0.30f, y, y, 400L, force = true, label = "TEST LEFT SWIPE")
    }

    fun dispatchTestRightSwipe(): Boolean {
        val (w, h) = screenSize()
        val y = h * 0.5f
        return dispatchStroke(w * 0.30f, w * 0.70f, y, y, 400L, force = true, label = "TEST RIGHT SWIPE")
    }

    fun dispatchTestCenterTap(): Boolean {
        val (w, h) = screenSize()
        val x = w * 0.50f
        val y = h * 0.50f
        return dispatchStroke(x, x, y, y, 50L, force = true, label = "TEST CENTER TAP")
    }

    fun performTestSwipe(): Boolean {
        val (w, h) = screenSize()
        val startX = w * 0.20f
        val endX = w * 0.80f
        val y = h * 0.50f
        Log.i(TAG, "TEST SWIPE REQUESTED start=($startX,$y) end=($endX,$y) duration=700ms")
        return dispatchStroke(startX, endX, y, y, 700L, force = true, label = "TEST REAL SWIPE")
    }

    // ------------------------------------------------------------------
    // Shared discrete stroke dispatch
    // ------------------------------------------------------------------

    private fun screenSize(): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        } catch (_: Throwable) {
            // fall through
        }
        val fallback = resources.displayMetrics
        val w = (if (metrics.widthPixels > 0) metrics.widthPixels else fallback.widthPixels).toFloat()
        val h = (if (metrics.heightPixels > 0) metrics.heightPixels else fallback.heightPixels).toFloat()
        return w.coerceAtLeast(1f) to h.coerceAtLeast(1f)
    }

    private fun dispatchStroke(
        startX: Float,
        endX: Float,
        startY: Float,
        endY: Float,
        durationMs: Long,
        force: Boolean,
        label: String?
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (label != null) Log.w(TAG, "$label rejected — API < 24")
            return false
        }
        if (force) {
            gestureInFlight.set(false)
            // Don't leave a virtual finger held across a manual test swipe.
            dragActive.set(false)
            continuingStroke = null
        }
        if (!gestureInFlight.compareAndSet(false, true)) {
            if (label != null) Log.d(TAG, "$label skipped — gesture already in flight")
            return false
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            durationMs.coerceIn(16L, 1000L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        if (label != null) Log.i(TAG, "GESTURE COMPLETED ($label)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        if (label != null) Log.w(TAG, "GESTURE CANCELLED ($label)")
                    }
                },
                null
            )
            Log.i(TAG, "dispatchGesture returned $accepted${if (label != null) " ($label)" else ""}")
            if (!accepted) {
                gestureInFlight.set(false)
                if (label != null) Log.w(TAG, "$label rejected by dispatchGesture()")
            } else if (label != null) {
                Log.i(TAG, "$label dispatched")
            }
            accepted
        } catch (t: Throwable) {
            gestureInFlight.set(false)
            Log.w(TAG, "${label ?: "dispatchGesture"} failed: ${t.message}")
            false
        }
    }
}
