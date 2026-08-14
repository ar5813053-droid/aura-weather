package com.aura.weather.handtracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Wraps the MediaPipe Hand Landmarker task in LIVE_STREAM mode and adapts
 * CameraX [ImageProxy] frames into MediaPipe [MPImage] input.
 *
 * REQUIRED MODEL FILE: the Hand Landmarker model
 * (`hand_landmarker.task`) must be placed at:
 *
 *     app/src/main/assets/hand_landmarker.task
 *
 * It is NOT bundled in this repository (it's a ~10MB binary asset). Download
 * it from Google's MediaPipe model index and drop it into that folder - see
 * app/src/main/assets/HAND_LANDMARKER_MODEL_README.md for the exact steps.
 * If the file is missing, [setup] fails gracefully and reports the problem
 * via [onError] instead of crashing.
 *
 * All MediaPipe/bitmap work here is expected to run off the UI thread: call
 * [setup] and [detect] from a background executor (see how MainActivity
 * wires this up with a dedicated single-thread executor for camera
 * analysis).
 */
class HandTracker(
    private val context: Context,
    private val maxHands: Int = 2,
    private val onResult: (HandTrackingResult) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"
    }

    // MediaPipe's LIVE_STREAM result/error listeners fire on an internal
    // MediaPipe worker thread, not the caller's thread. Both callbacks feed
    // straight into Compose state in MainActivity, so hop back to the main
    // thread before invoking them.
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var handLandmarker: HandLandmarker? = null

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * Loads the MediaPipe model and prepares the landmarker. Touches disk
     * (asset read) and native model parsing, so call this from a background
     * thread, not the UI thread.
     */
    fun setup() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(Delegate.CPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(maxHands)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener(::handleResult)
                .setErrorListener(::handleError)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isReady = true
        } catch (e: Exception) {
            isReady = false
            handLandmarker = null
            onError(
                "Couldn't load the hand tracking model. Make sure " +
                    "'hand_landmarker.task' is placed in app/src/main/assets/. " +
                    "(${e.message})"
            )
        }
    }

    /**
     * Feeds one CameraX frame into the hand landmarker. Always closes
     * [imageProxy] when done, regardless of outcome. Must be called from
     * the ImageAnalysis background executor, never the UI thread.
     */
    fun detect(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val landmarker = handLandmarker
        if (landmarker == null) {
            imageProxy.close()
            return
        }

        try {
            // ImageAnalysis is configured with OUTPUT_IMAGE_FORMAT_RGBA_8888,
            // so plane 0 is already a single packed RGBA buffer we can copy
            // straight into a Bitmap.
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
            )
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    // Mirror horizontally so landmarks line up with the
                    // selfie-mirrored preview the user actually sees.
                    postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
            )

            val mpImage: MPImage = BitmapImageBuilder(rotatedBitmap).build()
            val frameTimeMs = SystemClock.uptimeMillis()
            landmarker.detectAsync(mpImage, frameTimeMs)
        } catch (e: Exception) {
            onError("Hand tracking frame error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun handleResult(result: HandLandmarkerResult, input: MPImage) {
        val hands = result.landmarks().map { landmarkList ->
            DetectedHand(
                landmarks = landmarkList.map { lm ->
                    NormalizedPoint(x = lm.x(), y = lm.y(), z = lm.z())
                }
            )
        }
        val trackingResult = HandTrackingResult(
            hands = hands,
            imageWidth = input.width,
            imageHeight = input.height,
            inferenceTimeMs = SystemClock.uptimeMillis() - result.timestampMs()
        )
        mainHandler.post { onResult(trackingResult) }
    }

    private fun handleError(error: RuntimeException) {
        val message = error.message ?: "Unknown hand tracking error"
        mainHandler.post { onError(message) }
    }

    /**
     * Releases the underlying MediaPipe task. Must be called when tracking
     * stops for good (e.g. activity lifecycle cleanup) to avoid leaking the
     * native landmarker.
     */
    fun close() {
        try {
            handLandmarker?.close()
        } catch (_: Exception) {
            // Already released or failed to initialize - nothing to do.
        } finally {
            handLandmarker = null
            isReady = false
        }
    }
}

