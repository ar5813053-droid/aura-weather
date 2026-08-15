package com.aura.weather.input

import android.util.Log

/**
 * Phase-1 [InputController] that records steering state and optionally logs
 * it. It does **not** inject touches, synthetic gestures, or modify any
 * other application.
 *
 * This is the default controller wired from MainActivity until an
 * AccessibilityService-based implementation is ready.
 *
 * -------------------------------------------------------------------------
 * FUTURE CONNECTION POINT
 * -------------------------------------------------------------------------
 * Replace this class (or swap the instance created in MainActivity) with
 * something like `AccessibilityInputController` that:
 *
 * 1. Binds to a HandDrive AccessibilityService declared in the manifest
 *    (`android.accessibilityservice.AccessibilityService` + config XML).
 * 2. On [updateSteering], maps [SteeringInputState.steeringPercent] to
 *    game-appropriate input (e.g. left/right swipe regions, continuous
 *    gesture paths, or key events) on the foreground racing game.
 * 3. On [stop]/[reset], cancels any in-flight gestures so the game
 *    returns to neutral.
 *
 * No MediaPipe, camera, or two-hand steering code needs to change for that
 * step — only the [InputController] implementation behind this interface.
 * -------------------------------------------------------------------------
 *
 * @param logUpdates when true, writes throttled debug lines to logcat
 *   (tag `HandDriveInput`). Keep false for normal use to avoid spam.
 */
class PlaceholderInputController(
    private val logUpdates: Boolean = false
) : InputController {

    companion object {
        private const val TAG = "HandDriveInput"
        /** Minimum interval between log lines when [logUpdates] is true. */
        private const val LOG_THROTTLE_MS = 250L
    }

    @Volatile
    private var active: Boolean = false

    @Volatile
    private var _lastState: SteeringInputState = SteeringInputState.IDLE

    private var lastLogUptimeMs: Long = 0L

    override val isActive: Boolean
        get() = active

    override val lastState: SteeringInputState
        get() = _lastState

    override fun start() {
        active = true
        if (logUpdates) {
            Log.i(TAG, "InputController started (placeholder — no game injection)")
        }
    }

    override fun stop() {
        active = false
        reset()
        if (logUpdates) {
            Log.i(TAG, "InputController stopped")
        }
    }

    override fun updateSteering(state: SteeringInputState) {
        _lastState = state
        if (!active) return

        // Placeholder: hold state only. Future AccessibilityInputController
        // will translate state.steeringPercent into external input here.
        if (logUpdates) {
            val now = System.currentTimeMillis()
            if (now - lastLogUptimeMs >= LOG_THROTTLE_MS) {
                lastLogUptimeMs = now
                Log.d(
                    TAG,
                    "steering=%5.1f%% angle=%5.1f° hands=%d tracking=%s".format(
                        state.steeringPercent,
                        state.steeringAngleDegrees,
                        state.handsDetected,
                        state.isTracking
                    )
                )
            }
        }
    }

    override fun reset() {
        _lastState = SteeringInputState.IDLE
    }
}
