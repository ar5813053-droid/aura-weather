package com.aura.weather.steering

import com.aura.weather.handtracking.DetectedHand

/**
 * High-level orchestrator for steering input.
 *
 * Flow:
 * ```
 * HandTracker → DetectedHand list → SteeringController
 *   → OneHandSteeringStrategy | TwoHandSteeringStrategy
 *   → SteeringSmoother → SteeringOutput (+ SteeringDebugInfo)
 * ```
 *
 * Selection:
 * - 2 hands → [primaryStrategy] (default [TwoHandSteeringStrategy])
 * - 1 hand  → [fallbackStrategy] (default [OneHandSteeringStrategy]) when
 *   [autoFallback] is true
 * - 0 hands → target center; after [handLostTimeoutMs] the active strategy
 *   is [reset] so the next one-hand session re-calibrates
 *
 * Existing MainActivity code that still uses [SteeringCalculator] directly
 * is unaffected — this class is additive.
 */
class SteeringController(
    private val primaryStrategy: SteeringStrategy = TwoHandSteeringStrategy(),
    private val fallbackStrategy: SteeringStrategy = OneHandSteeringStrategy(),
    private val smoother: SteeringSmoother? = SteeringSmoother(),
    private var autoFallback: Boolean = true,
    /** After this many ms with zero hands, strategies are reset (re-calibrate). */
    private val handLostTimeoutMs: Long = 400L
) {

    var activeStrategy: SteeringStrategy = primaryStrategy
        private set

    var lastDebugInfo: SteeringDebugInfo = SteeringDebugInfo()
        private set

    private var lastHandSeenUptimeMs: Long = 0L
    private var handsCurrentlyVisible: Boolean = false
    private var lossResetDone: Boolean = true

    /**
     * Process one frame from a list of [DetectedHand] (any order).
     * Hands are sorted left-to-right internally.
     */
    fun process(hands: List<DetectedHand>, uptimeMs: Long = System.currentTimeMillis()): SteeringOutput {
        val frame = SteeringFrame.fromHands(hands)
        return process(frame, uptimeMs)
    }

    fun process(frame: SteeringFrame, uptimeMs: Long = System.currentTimeMillis()): SteeringOutput {
        val count = frame.handCount

        if (count > 0) {
            lastHandSeenUptimeMs = uptimeMs
            handsCurrentlyVisible = true
            lossResetDone = false
        } else {
            handsCurrentlyVisible = false
            val lostFor = uptimeMs - lastHandSeenUptimeMs
            if (!lossResetDone && lastHandSeenUptimeMs > 0L && lostFor >= handLostTimeoutMs) {
                primaryStrategy.reset()
                fallbackStrategy.reset()
                lossResetDone = true
            }
        }

        activeStrategy = when {
            autoFallback && count == 1 -> fallbackStrategy
            count >= 2 -> primaryStrategy
            count == 1 -> primaryStrategy // autoFallback off: still try primary
            else -> activeStrategy // keep last; will emit center below
        }

        val raw = if (count == 0) {
            SteeringOutput.CENTER
        } else {
            activeStrategy.calculate(frame)
        }

        val smoothed = smoother?.smooth(raw) ?: raw

        val strategyDebug = if (count == 0) {
            SteeringDebugInfo(
                targetSteeringAngleDegrees = smoothed.angleDegrees,
                isCalibrated = false,
                handDetected = false,
                activeMode = "none"
            )
        } else {
            activeStrategy.lastDebugInfo().copy(
                targetSteeringAngleDegrees = smoothed.angleDegrees,
                handDetected = true,
                activeMode = when (activeStrategy) {
                    is OneHandSteeringStrategy -> "one_hand"
                    is TwoHandSteeringStrategy -> "two_hand"
                    else -> "custom"
                }
            )
        }
        lastDebugInfo = strategyDebug

        return smoothed
    }

    fun usePrimaryStrategy() {
        autoFallback = false
        activeStrategy = primaryStrategy
    }

    fun useFallbackStrategy() {
        autoFallback = false
        activeStrategy = fallbackStrategy
    }

    fun enableAutoFallback() {
        autoFallback = true
    }

    /** Reset smoother and both strategies (new session). */
    fun reset() {
        smoother?.reset()
        primaryStrategy.reset()
        fallbackStrategy.reset()
        activeStrategy = primaryStrategy
        lastDebugInfo = SteeringDebugInfo()
        lastHandSeenUptimeMs = 0L
        handsCurrentlyVisible = false
        lossResetDone = true
    }
}
