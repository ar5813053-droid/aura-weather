package com.aura.weather.steering

/**
 * Strategy interface for turning tracked hands into a steering reading for
 * a single frame.
 *
 * Implementations interpret the current frame only with respect to their
 * own internal calibration state (if any). Frame-to-frame output smoothing
 * is left to [SteeringSmoother] / [SteeringController].
 *
 * - [TwoHandSteeringStrategy] — line tilt between left and right wrists
 *   (same math as [SteeringCalculator]).
 * - [OneHandSteeringStrategy] — palm orientation (wrist → middle MCP)
 *   relative to a one-time neutral calibration.
 */
interface SteeringStrategy {

    /**
     * Compute target steering for this frame.
     *
     * Prefer [SteeringOutput.CENTER] when there is not enough input so a
     * downstream smoother can ease back to neutral.
     */
    fun calculate(frame: SteeringFrame): SteeringOutput

    /**
     * Optional debug data for the last [calculate] call. Default is empty;
     * [OneHandSteeringStrategy] fills palm angles / calibration state.
     */
    fun lastDebugInfo(): SteeringDebugInfo = SteeringDebugInfo()

    /**
     * Clear any internal calibration / session state (e.g. after the hand
     * has been lost long enough that a new one-hand session should start).
     */
    fun reset() {}
}
