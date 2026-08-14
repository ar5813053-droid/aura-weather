package com.aura.weather.steering

import kotlin.math.abs
import kotlin.math.atan2

/**
 * Tunable parameters for [SteeringCalculator].
 *
 * @param deadZoneDegrees angles smaller than this (in either direction) are
 *   treated as dead-center. Prevents jitter/noise from producing steering
 *   input when the hands are roughly level.
 * @param sensitivity multiplier applied to the raw hand-tilt angle after the
 *   dead zone is removed. Higher = a smaller physical hand tilt produces a
 *   larger steering response.
 * @param maxSteeringAngleDegrees the hand-tilt angle (post sensitivity) that
 *   maps to full lock (+/-100%). Also used to clamp the output angle.
 */
data class SteeringConfig(
    val deadZoneDegrees: Float = 4f,
    val sensitivity: Float = 1.6f,
    val maxSteeringAngleDegrees: Float = 45f
)

/**
 * A single point in normalized image space (x/y in [0, 1], origin top-left),
 * i.e. what a MediaPipe hand landmark (such as the wrist) looks like.
 */
data class HandPoint(val x: Float, val y: Float)

/** Result of a steering calculation for a single frame. */
data class SteeringOutput(
    val angleDegrees: Float,
    val steeringPercent: Float
) {
    companion object {
        val CENTER = SteeringOutput(angleDegrees = 0f, steeringPercent = 0f)
    }
}

/**
 * Converts the positions of two tracked hands into a virtual steering-wheel
 * reading, as if the two hands were gripping an invisible wheel.
 *
 * The steering value is derived from the *orientation* of the line between
 * the left and right hand (i.e. how much the imaginary wheel is tilted),
 * never from the absolute X position of a single hand.
 *
 * This class is pure/stateless (no smoothing, no Android dependencies) so it
 * can be reused directly by a future game-control system. Pair it with
 * [SteeringSmoother] for frame-to-frame smoothing.
 */
class SteeringCalculator(private val config: SteeringConfig = SteeringConfig()) {

    /**
     * @param leftHand the hand positioned to the left (smaller x). Null if
     *   fewer than two hands are currently tracked.
     * @param rightHand the hand positioned to the right (larger x). Null if
     *   fewer than two hands are currently tracked.
     * @return [SteeringOutput.CENTER] whenever fewer than two hands are
     *   available, so the caller can safely feed that straight into
     *   [SteeringSmoother] and the wheel will ease back to center.
     */
    fun calculate(leftHand: HandPoint?, rightHand: HandPoint?): SteeringOutput {
        if (leftHand == null || rightHand == null) {
            return SteeringOutput.CENTER
        }

        val dx = rightHand.x - leftHand.x
        val dy = rightHand.y - leftHand.y
        if (dx == 0f && dy == 0f) {
            return SteeringOutput.CENTER
        }

        // Image-space y grows downward, so a positive angle here means the
        // right hand is lower than the left hand - i.e. the wheel is
        // rotated clockwise ("turning right"), which matches intuition.
        val rawAngleDegrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

        val deadZoned = applyDeadZone(rawAngleDegrees)
        val amplified = deadZoned * config.sensitivity
        val clampedAngle = amplified.coerceIn(
            -config.maxSteeringAngleDegrees,
            config.maxSteeringAngleDegrees
        )
        val percent = (clampedAngle / config.maxSteeringAngleDegrees * 100f)
            .coerceIn(-100f, 100f)

        return SteeringOutput(angleDegrees = clampedAngle, steeringPercent = percent)
    }

    private fun applyDeadZone(angleDegrees: Float): Float {
        if (abs(angleDegrees) < config.deadZoneDegrees) return 0f
        val sign = if (angleDegrees > 0) 1f else -1f
        return sign * (abs(angleDegrees) - config.deadZoneDegrees)
    }
}

