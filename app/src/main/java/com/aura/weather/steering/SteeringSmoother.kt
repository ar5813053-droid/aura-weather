package com.aura.weather.steering

/**
 * Applies exponential moving average (EMA) smoothing to [SteeringOutput]
 * values across frames, so noisy per-frame hand-landmark jitter doesn't
 * translate into a jumpy steering readout.
 *
 * Feed it every frame's raw [SteeringCalculator] output - including
 * [SteeringOutput.CENTER] on frames where fewer than two hands are
 * detected - and it will smoothly ease toward the target rather than
 * snapping.
 *
 * @param smoothingFactor how much of the new value to blend in each frame,
 *   in (0, 1]. Smaller = smoother but more laggy, larger = more responsive
 *   but more jittery. 1.0 disables smoothing entirely.
 */
class SteeringSmoother(private val smoothingFactor: Float = 0.25f) {

    private var smoothedAngle = 0f
    private var smoothedPercent = 0f

    fun smooth(target: SteeringOutput): SteeringOutput {
        smoothedAngle += (target.angleDegrees - smoothedAngle) * smoothingFactor
        smoothedPercent += (target.steeringPercent - smoothedPercent) * smoothingFactor
        return SteeringOutput(angleDegrees = smoothedAngle, steeringPercent = smoothedPercent)
    }

    /** Resets the smoother back to dead-center, e.g. when tracking stops. */
    fun reset() {
        smoothedAngle = 0f
        smoothedPercent = 0f
    }
}
