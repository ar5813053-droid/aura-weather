package com.aura.weather.steering

/**
 * Two-hand steering strategy: treats the line between the left and right
 * wrists as an invisible steering wheel and derives the turn angle from its
 * tilt.
 *
 * Delegates entirely to [SteeringCalculator] so dead-zone, sensitivity and
 * clamp behaviour stay identical to the original calculator path used by
 * MainActivity. Only the input type changed (wrists extracted from
 * [SteeringFrame]); the mathematics are unchanged.
 *
 * Requires both hands. When either is missing returns [SteeringOutput.CENTER].
 */
class TwoHandSteeringStrategy(
    config: SteeringConfig = SteeringConfig()
) : SteeringStrategy {

    private val calculator = SteeringCalculator(config)

    override fun calculate(frame: SteeringFrame): SteeringOutput {
        val left = frame.left?.let { HandPoint(it.wrist.x, it.wrist.y) }
        val right = frame.right?.let { HandPoint(it.wrist.x, it.wrist.y) }
        return calculator.calculate(left, right)
    }

    override fun lastDebugInfo(): SteeringDebugInfo = SteeringDebugInfo(
        targetSteeringAngleDegrees = 0f,
        handDetected = false,
        activeMode = "two_hand"
    )
}
