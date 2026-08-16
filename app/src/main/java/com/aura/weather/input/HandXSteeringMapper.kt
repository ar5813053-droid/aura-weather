package com.aura.weather.input

import kotlin.math.abs

/**
 * Maps a normalized hand X coordinate (MediaPipe landmark space [0, 1],
 * origin top-left, already mirrored for the front camera in [HandTracker])
 * into a steering value in [-100, +100].
 *
 * Pipeline: raw → dead zone → sensitivity → EMA smoothing → clamp maxRange.
 *
 * Defaults are fixed in code (no settings UI yet).
 */
class HandXSteeringMapper(
    var deadZone: Float = 10f,
    var sensitivity: Float = 1.0f,
    var smoothing: Float = 0.20f,
    var maxRange: Float = 100f
) {
    data class Result(
        val handX: Float,
        val rawSteering: Float,
        val steering: Float
    )

    private var smoothed: Float = 0f

    /**
     * @param handX normalized wrist x in [0, 1]; null if no hand
     */
    fun process(handX: Float?): Result {
        if (handX == null) {
            // Ease toward center when tracking is lost.
            smoothed += (0f - smoothed) * smoothing.coerceIn(0.01f, 1f)
            if (abs(smoothed) < 0.5f) smoothed = 0f
            return Result(handX = Float.NaN, rawSteering = 0f, steering = smoothed)
        }

        val x = handX.coerceIn(0f, 1f)
        // 0 → -100 (left), 0.5 → 0, 1 → +100 (right)
        val raw = ((x - 0.5f) * 2f * 100f).coerceIn(-100f, 100f)

        val afterDeadZone = applyDeadZone(raw)
        val afterSensitivity = (afterDeadZone * sensitivity).coerceIn(-maxRange, maxRange)

        val alpha = smoothing.coerceIn(0.01f, 1f)
        smoothed += (afterSensitivity - smoothed) * alpha
        val finalSteering = smoothed.coerceIn(-maxRange, maxRange)

        return Result(handX = x, rawSteering = raw, steering = finalSteering)
    }

    fun reset() {
        smoothed = 0f
    }

    /**
     * Dead zone on |raw| <= [deadZone]: output 0.
     * Outside: remap remaining range so full lock is still reachable at ±100 raw.
     */
    private fun applyDeadZone(raw: Float): Float {
        val dz = deadZone.coerceIn(0f, 50f)
        val absRaw = abs(raw)
        if (absRaw <= dz) return 0f
        val sign = if (raw >= 0f) 1f else -1f
        val usable = (100f - dz).coerceAtLeast(1f)
        return sign * ((absRaw - dz) / usable) * 100f
    }
}
