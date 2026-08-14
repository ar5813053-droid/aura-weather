package com.aura.weather.handtracking

/** A single hand landmark in normalized image space (x/y/z in [0, 1] range). */
data class NormalizedPoint(val x: Float, val y: Float, val z: Float = 0f)

/**
 * One detected hand: the full 21-point MediaPipe hand landmark list, in
 * MediaPipe's fixed landmark order (index 0 is always the wrist).
 */
data class DetectedHand(val landmarks: List<NormalizedPoint>) {
    /** Stable landmark used for steering: the wrist (MediaPipe landmark 0). */
    val wrist: NormalizedPoint get() = landmarks[0]
}

/** Result of running the hand landmarker on a single camera frame. */
data class HandTrackingResult(
    val hands: List<DetectedHand>,
    val imageWidth: Int,
    val imageHeight: Int,
    val inferenceTimeMs: Long
) {
    companion object {
        val EMPTY = HandTrackingResult(
            hands = emptyList(),
            imageWidth = 0,
            imageHeight = 0,
            inferenceTimeMs = 0
        )
    }
}
