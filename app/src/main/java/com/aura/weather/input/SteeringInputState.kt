package com.aura.weather.input

import com.aura.weather.steering.SteeringOutput

/**
 * Snapshot of steering + tracking state delivered to an [InputController]
 * each frame (or on tracking start/stop).
 *
 * Built from the existing [SteeringOutput] produced by
 * [com.aura.weather.steering.SteeringCalculator] +
 * [com.aura.weather.steering.SteeringSmoother]. No new steering math lives
 * here — this is pure transport into the input-control layer.
 *
 * @param steeringPercent  −100 … +100 (negative = left, positive = right)
 * @param steeringAngleDegrees clamped wheel angle from the two-hand calculator
 * @param handsDetected    number of hands reported by MediaPipe this frame
 * @param handDetected     convenience: handsDetected > 0
 * @param isTracking       whether HandDrive camera/tracking is currently active
 */
data class SteeringInputState(
    val steeringPercent: Float = 0f,
    val steeringAngleDegrees: Float = 0f,
    val handsDetected: Int = 0,
    val handDetected: Boolean = false,
    val isTracking: Boolean = false
) {
    companion object {
        val IDLE = SteeringInputState()

        fun from(
            output: SteeringOutput,
            handsDetected: Int,
            isTracking: Boolean
        ): SteeringInputState = SteeringInputState(
            steeringPercent = output.steeringPercent,
            steeringAngleDegrees = output.angleDegrees,
            handsDetected = handsDetected,
            handDetected = handsDetected > 0,
            isTracking = isTracking
        )
    }
}
