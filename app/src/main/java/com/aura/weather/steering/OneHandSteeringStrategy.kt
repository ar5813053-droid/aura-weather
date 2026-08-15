package com.aura.weather.steering

import com.aura.weather.handtracking.DetectedHand
import com.aura.weather.handtracking.NormalizedPoint
import kotlin.math.abs
import kotlin.math.atan2

/**
 * One-hand steering from **palm orientation**, not hand X position.
 *
 * Palm axis: MediaPipe wrist (landmark 0) → middle-finger MCP (landmark 9).
 *
 * Coordinate convention
 * ---------------------
 * Landmarks are in normalized image space: origin top-left, x right, **y down**.
 * [atan2](dy, dx) therefore yields:
 * - 0° when the axis points right
 * - +90° when the axis points down
 * - ±180° when the axis points left
 * - -90° when the axis points up
 *
 * At the start of a one-hand session the strategy collects stable samples of
 * this angle and stores them as the **neutral** orientation. After that:
 *
 *     relativeAngle = normalize(currentAngle - neutralAngle)   // [-180, +180]
 *
 * Mapping (after dead zone + sensitivity + clamp from [SteeringConfig]):
 * - relative −90° → full left  (−maxSteeringAngle / −100%)
 * - relative   0° → center
 * - relative +90° → full right (+maxSteeringAngle / +100%)
 *
 * Neutral is calibrated **once** per session. It is cleared only via [reset]
 * (hand-loss timeout handled by [SteeringController]).
 *
 * This class does **not** use `hand.x - centerX` for steering.
 */
class OneHandSteeringStrategy(
    private val config: SteeringConfig = SteeringConfig(),
    /** Degrees of palm rotation (relative) that map to full lock. */
    private val fullLockRelativeDegrees: Float = 90f,
    /** How many consecutive stable samples are required before neutral is set. */
    private val calibrationSampleCount: Int = 12,
    /** Max peak-to-peak spread (degrees) among calibration samples to accept. */
    private val calibrationStabilityDegrees: Float = 6f
) : SteeringStrategy {

    companion object {
        /** MediaPipe hand landmark indices. */
        const val WRIST = 0
        const val MIDDLE_FINGER_MCP = 9
    }

    private var neutralAngleDegrees: Float? = null
    private val calibrationSamples = ArrayDeque<Float>()
    private var lastPalmAngle: Float? = null
    private var lastRelativeAngle: Float? = null
    private var lastTargetAngle: Float = 0f
    private var lastHandDetected: Boolean = false

    val isCalibrated: Boolean
        get() = neutralAngleDegrees != null

    override fun calculate(frame: SteeringFrame): SteeringOutput {
        val hand = frame.singleHand
        if (hand == null || hand.landmarks.size <= MIDDLE_FINGER_MCP) {
            lastHandDetected = false
            lastPalmAngle = null
            lastRelativeAngle = null
            lastTargetAngle = 0f
            return SteeringOutput.CENTER
        }

        lastHandDetected = true
        val palmAngle = palmAngleDegrees(hand) ?: run {
            lastPalmAngle = null
            lastRelativeAngle = null
            lastTargetAngle = 0f
            return SteeringOutput.CENTER
        }
        lastPalmAngle = palmAngle

        val neutral = neutralAngleDegrees
        if (neutral == null) {
            collectCalibrationSample(palmAngle)
            lastRelativeAngle = null
            lastTargetAngle = 0f
            // Still calibrating — hold center so smoother stays quiet.
            return SteeringOutput.CENTER
        }

        val relative = normalizeAngleDegrees(palmAngle - neutral)
        lastRelativeAngle = relative

        val output = mapRelativeToSteering(relative)
        lastTargetAngle = output.angleDegrees
        return output
    }

    override fun lastDebugInfo(): SteeringDebugInfo = SteeringDebugInfo(
        palmAngleDegrees = lastPalmAngle,
        neutralPalmAngleDegrees = neutralAngleDegrees,
        relativeAngleDegrees = lastRelativeAngle,
        targetSteeringAngleDegrees = lastTargetAngle,
        isCalibrated = isCalibrated,
        handDetected = lastHandDetected,
        activeMode = "one_hand"
    )

    override fun reset() {
        neutralAngleDegrees = null
        calibrationSamples.clear()
        lastPalmAngle = null
        lastRelativeAngle = null
        lastTargetAngle = 0f
        lastHandDetected = false
    }

    // -------------------------------------------------------------------------
    // Palm angle
    // -------------------------------------------------------------------------

    /**
     * Angle of the wrist → middle-MCP vector in image space (degrees).
     * Returns null if the two landmarks coincide.
     */
    private fun palmAngleDegrees(hand: DetectedHand): Float? {
        val wrist = hand.landmarks[WRIST]
        val middleMcp = hand.landmarks[MIDDLE_FINGER_MCP]
        return axisAngleDegrees(wrist, middleMcp)
    }

    private fun axisAngleDegrees(from: NormalizedPoint, to: NormalizedPoint): Float? {
        val dx = to.x - from.x
        val dy = to.y - from.y
        if (dx == 0f && dy == 0f) return null
        // y grows downward in image space; atan2(dy, dx) is intentional.
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    // -------------------------------------------------------------------------
    // Calibration (once per session)
    // -------------------------------------------------------------------------

    private fun collectCalibrationSample(angle: Float) {
        calibrationSamples.addLast(angle)
        while (calibrationSamples.size > calibrationSampleCount) {
            calibrationSamples.removeFirst()
        }
        if (calibrationSamples.size < calibrationSampleCount) return

        val spread = circularSpreadDegrees(calibrationSamples)
        if (spread <= calibrationStabilityDegrees) {
            neutralAngleDegrees = circularMeanDegrees(calibrationSamples)
            calibrationSamples.clear()
        }
    }

    // -------------------------------------------------------------------------
    // Mapping relative palm rotation → SteeringOutput
    // -------------------------------------------------------------------------

    private fun mapRelativeToSteering(relativeDegrees: Float): SteeringOutput {
        val deadZoned = applyDeadZone(relativeDegrees)
        // Scale so ±fullLockRelativeDegrees maps toward ±maxSteeringAngleDegrees
        // before sensitivity, then apply sensitivity and clamp.
        val scaled = (deadZoned / fullLockRelativeDegrees) * config.maxSteeringAngleDegrees
        val amplified = scaled * config.sensitivity
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
        val sign = if (angleDegrees > 0f) 1f else -1f
        return sign * (abs(angleDegrees) - config.deadZoneDegrees)
    }

    // -------------------------------------------------------------------------
    // Angle helpers (degrees, circular)
    // -------------------------------------------------------------------------

    /** Normalize to (−180, +180]. */
    private fun normalizeAngleDegrees(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a <= -180f) a += 360f
        return a
    }

    private fun circularMeanDegrees(samples: Collection<Float>): Float {
        if (samples.isEmpty()) return 0f
        var sinSum = 0.0
        var cosSum = 0.0
        for (s in samples) {
            val r = Math.toRadians(s.toDouble())
            sinSum += kotlin.math.sin(r)
            cosSum += kotlin.math.cos(r)
        }
        val n = samples.size.toDouble()
        return Math.toDegrees(atan2(sinSum / n, cosSum / n)).toFloat()
            .let { normalizeAngleDegrees(it) }
    }

    private fun circularSpreadDegrees(samples: Collection<Float>): Float {
        if (samples.size < 2) return 0f
        val mean = circularMeanDegrees(samples)
        var maxDev = 0f
        for (s in samples) {
            val d = abs(normalizeAngleDegrees(s - mean))
            if (d > maxDev) maxDev = d
        }
        // Peak-to-peak approximation: 2 * max deviation from mean.
        return maxDev * 2f
    }
}
