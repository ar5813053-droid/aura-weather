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

/**
 * AccessibilityService used as a gesture injection endpoint for HandDrive.
 *
 * [instance] is set in [onServiceConnected] and cleared in [onDestroy].
 * Steering uses [dispatchSteeringGesture]; temporary diagnostic buttons use
 * [dispatchTestLeftSwipe], [dispatchTestRightSwipe], [dispatchTestCenterTap].
 */
class HandDriveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HandDriveInput"

        private val instanceRef = AtomicReference<HandDriveAccessibilityService?>(null)

        val instance: HandDriveAccessibilityService?
            get() = instanceRef.get()

        fun isConnected(): Boolean = instanceRef.get() != null

        /** Safe helpers for UI test buttons (no-op if service not connected). */
        fun testLeftSwipe(): Boolean =
            instance?.dispatchTestLeftSwipe() ?: logNotConnected("TEST LEFT SWIPE")

        fun testRightSwipe(): Boolean =
            instance?.dispatchTestRightSwipe() ?: logNotConnected("TEST RIGHT SWIPE")

        fun testCenterTap(): Boolean =
            instance?.dispatchTestCenterTap() ?: logNotConnected("TEST CENTER TAP")

        private fun logNotConnected(label: String): Boolean {
            Log.w(TAG, "$label rejected — HandDriveAccessibilityService NOT CONNECTED")
            return false
        }
    }

    private val gestureInFlight = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Gesture dispatch only; no event processing.
    }

    override fun onInterrupt() {
        gestureInFlight.set(false)
    }

    override fun onDestroy() {
        instanceRef.compareAndSet(this, null)
        gestureInFlight.set(false)
        Log.i(TAG, "service destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------
    // Steering path (used by AccessibilityInputController)
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
    }

    // ------------------------------------------------------------------
    // Temporary diagnostic test gestures (work with tracking OFF)
    // ------------------------------------------------------------------

    /**
     * Left swipe: ~70% → 30% width at mid height, ~400 ms.
     */
    fun dispatchTestLeftSwipe(): Boolean {
        val (w, h) = screenSize()
        val y = h * 0.5f
        val startX = w * 0.70f
        val endX = w * 0.30f
        return dispatchStroke(
            startX = startX,
            endX = endX,
            startY = y,
            endY = y,
            durationMs = 400L,
            force = true,
            label = "TEST LEFT SWIPE"
        )
    }

    /**
     * Right swipe: ~30% → 70% width at mid height, ~400 ms.
     */
    fun dispatchTestRightSwipe(): Boolean {
        val (w, h) = screenSize()
        val y = h * 0.5f
        val startX = w * 0.30f
        val endX = w * 0.70f
        return dispatchStroke(
            startX = startX,
            endX = endX,
            startY = y,
            endY = y,
            durationMs = 400L,
            force = true,
            label = "TEST RIGHT SWIPE"
        )
    }

    /**
     * Center tap: ~50%/50%, short duration click-style stroke.
     */
    fun dispatchTestCenterTap(): Boolean {
        val (w, h) = screenSize()
        val x = w * 0.50f
        val y = h * 0.50f
        // Single-point path ≈ click for dispatchGesture.
        return dispatchStroke(
            startX = x,
            endX = x,
            startY = y,
            endY = y,
            durationMs = 50L,
            force = true,
            label = "TEST CENTER TAP"
        )
    }

    // ------------------------------------------------------------------
    // Shared dispatch
    // ------------------------------------------------------------------

    private fun screenSize(): Pair<Float, Float> {
        val metrics = DisplayMetrics()
        // API 24+ compatible: WindowManager.defaultDisplay (not Context.getDisplay(), API 30+).
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        } catch (_: Throwable) {
            // Fall through to resources metrics.
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
            /* startTime = */ 0L,
            /* duration = */ durationMs.coerceIn(16L, 800L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        if (label != null) {
                            Log.d(TAG, "$label completed")
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                        if (label != null) {
                            Log.w(TAG, "$label cancelled by system")
                        }
                    }
                },
                /* handler = */ null
            )
            if (accepted) {
                if (label != null) {
                    Log.i(TAG, "$label dispatched")
                }
            } else {
                gestureInFlight.set(false)
                if (label != null) {
                    Log.w(TAG, "$label rejected by dispatchGesture()")
                } else {
                    Log.d(TAG, "dispatchGesture rejected by system")
                }
            }
            accepted
        } catch (t: Throwable) {
            gestureInFlight.set(false)
            Log.w(TAG, "${label ?: "dispatchGesture"} failed: ${t.message}")
            false
        }
    }
}
