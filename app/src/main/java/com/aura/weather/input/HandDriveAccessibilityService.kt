package com.aura.weather.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * AccessibilityService used solely as a **gesture injection endpoint** for
 * HandDrive's [AccessibilityInputController].
 *
 * It does not process accessibility events for UI automation beyond what is
 * required to keep the service alive. External code obtains the live instance
 * via [instance] and calls [dispatchSteeringGesture].
 *
 * Lifecycle safety:
 * - [instance] is set in [onServiceConnected] and cleared in [onDestroy]
 * - All public entry points tolerate a null / destroyed service
 *
 * This is a **prototype**. Games vary widely in how they interpret touches;
 * success is not guaranteed for Monoposto, Traffic Racer, Real Racing, etc.
 */
class HandDriveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HandDriveA11y"

        /** Currently running service, or null if not connected. Thread-safe. */
        private val instanceRef = AtomicReference<HandDriveAccessibilityService?>(null)

        val instance: HandDriveAccessibilityService?
            get() = instanceRef.get()

        fun isConnected(): Boolean = instanceRef.get() != null
    }

    private val gestureInFlight = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef.set(this)
        Log.i(TAG, "HandDrive accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — we only need gesture dispatch capability.
    }

    override fun onInterrupt() {
        // System requested interrupt; cancel any in-flight bookkeeping.
        gestureInFlight.set(false)
    }

    override fun onDestroy() {
        instanceRef.compareAndSet(this, null)
        gestureInFlight.set(false)
        Log.i(TAG, "HandDrive accessibility service destroyed")
        super.onDestroy()
    }

    /**
     * Dispatch a short horizontal stroke representing steering.
     *
     * @param startX start of stroke in screen pixels
     * @param endX   end of stroke in screen pixels
     * @param y      constant vertical position (usually mid-screen)
     * @param durationMs stroke duration
     * @return true if the system accepted the gesture request
     */
    fun dispatchSteeringGesture(
        startX: Float,
        endX: Float,
        y: Float,
        durationMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return false
        }
        // Avoid stacking overlapping gestures; skip if one is still running.
        if (!gestureInFlight.compareAndSet(false, true)) {
            return false
        }

        val path = Path().apply {
            moveTo(startX, y)
            lineTo(endX, y)
        }
        val stroke = GestureDescription.StrokeDescription(
            path,
            /* startTime = */ 0L,
            /* duration = */ durationMs.coerceIn(16L, 500L)
        )
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return try {
            val accepted = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        gestureInFlight.set(false)
                    }
                },
                /* handler = */ null
            )
            if (!accepted) {
                gestureInFlight.set(false)
            }
            accepted
        } catch (t: Throwable) {
            gestureInFlight.set(false)
            Log.w(TAG, "dispatchGesture failed: ${t.message}")
            false
        }
    }

    /** Best-effort cancel of the current gesture stream (API 30+). */
    fun cancelActiveGestures() {
        gestureInFlight.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                // endOrCancel is not public; clearing the flag is enough for
                // our throttle so the next stroke is allowed.
            } catch (_: Throwable) {
                // ignore
            }
        }
    }
}
