package com.aura.weather.steering

import com.aura.weather.handtracking.DetectedHand

/**
 * One frame of hand detections for steering strategies.
 *
 * Hands are ordered left-to-right by wrist x (smaller x = left). This is the
 * same ordering MainActivity already uses when building HandPoint pairs, so
 * two-hand math stays identical when wrists are extracted.
 *
 * Carrying [DetectedHand] (full 21 MediaPipe landmarks) is the smallest
 * change that lets [OneHandSteeringStrategy] read palm orientation without
 * a second tracking pipeline.
 */
data class SteeringFrame(
    val left: DetectedHand? = null,
    val right: DetectedHand? = null
) {
    val handCount: Int
        get() = (if (left != null) 1 else 0) + (if (right != null) 1 else 0)

    val singleHand: DetectedHand?
        get() = when (handCount) {
            1 -> left ?: right
            else -> null
        }

    companion object {
        val EMPTY = SteeringFrame()

        /** Sort by wrist x and pack into left / right slots. */
        fun fromHands(hands: List<DetectedHand>): SteeringFrame {
            if (hands.isEmpty()) return EMPTY
            val sorted = hands.sortedBy { it.wrist.x }
            return SteeringFrame(
                left = sorted.getOrNull(0),
                right = sorted.getOrNull(1)
            )
        }
    }
}

/**
 * Debug snapshot for one-hand (and overall) steering. Filled by
 * [SteeringController] each frame; safe to ignore in production UI.
 */
data class SteeringDebugInfo(
    val palmAngleDegrees: Float? = null,
    val neutralPalmAngleDegrees: Float? = null,
    val relativeAngleDegrees: Float? = null,
    val targetSteeringAngleDegrees: Float = 0f,
    val isCalibrated: Boolean = false,
    val handDetected: Boolean = false,
    val activeMode: String = "none"
)
