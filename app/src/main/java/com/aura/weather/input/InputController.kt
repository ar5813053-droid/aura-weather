package com.aura.weather.input

/**
 * Abstraction between HandDrive's steering pipeline and whatever mechanism
 * will eventually drive a foreground racing game (Monoposto, Traffic Racer,
 * Real Racing, etc.).
 *
 * Phase 1 (this interface + [PlaceholderInputController]):
 * - Receives smoothed two-hand [SteeringInputState] from MainActivity
 * - Does **not** inject touches, keys, or Accessibility events
 * - Exists so UI / steering stay decoupled from future game-control code
 *
 * Future phase (not implemented here):
 * - An [InputController] backed by an Android [android.accessibilityservice.AccessibilityService]
 *   that maps [SteeringInputState.steeringPercent] to gestures or key events
 *   on the target game while HandDrive continues running (e.g. as a service
 *   or overlay). That implementation will live in this same package and will
 *   be selected instead of [PlaceholderInputController] without changing
 *   the MediaPipe → steering path.
 *
 * Lifecycle:
 * ```
 * start()            // tracking / session begins
 * updateSteering(..) // every frame while tracking
 * stop()             // tracking ends
 * reset()            // clear internal state (also called from stop)
 * ```
 */
interface InputController {

    /** Whether [start] has been called and [stop] has not yet. */
    val isActive: Boolean

    /** Last state passed to [updateSteering], or [SteeringInputState.IDLE]. */
    val lastState: SteeringInputState

    /**
     * Begin accepting steering updates. Called when the user starts hand
     * tracking. Idempotent.
     */
    fun start()

    /**
     * Stop accepting steering updates and release any session resources.
     * Should leave external games unaffected in the placeholder; the future
     * Accessibility implementation will release gesture streams here.
     */
    fun stop()

    /**
     * Deliver the latest smoothed steering sample.
     *
     * Called from the hand-tracking result path (background / MediaPipe
     * callback thread). Implementations must be thread-safe or post work
     * to their own handler.
     *
     * @param state combined steering + tracking snapshot for this frame
     */
    fun updateSteering(state: SteeringInputState)

    /**
     * Clear internal state back to neutral (e.g. when hands are lost or
     * tracking is stopped). Does not imply [stop] unless the implementation
     * chooses to.
     */
    fun reset()
}
