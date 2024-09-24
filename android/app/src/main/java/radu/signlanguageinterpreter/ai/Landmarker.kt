package radu.signlanguageinterpreter.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.globals.loadModelFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Suppress("PrivatePropertyName")
class Landmarker(
    private val context: Context,
    private val landmarkerListener: LandmarkerListener,
) {
    private val NUM_HANDS = 2
    private val MIN_HAND_DETECTION_CONFIDENCE = 0.6f
    private val MIN_HAND_PRESENCE_CONFIDENCE = 0.6f
    private val MIN_HAND_TRACKING_CONFIDENCE = 0.5f

    private val MIN_POSE_DETECTION_CONFIDENCE = 0.7f
    private val MIN_POSE_PRESENCE_CONFIDENCE = 0.7f
    private val MIN_POSE_TRACKING_CONFIDENCE = 0.5f

    private var handLandmarker: HandLandmarker? = null
    private var poseLandmarker: PoseLandmarker? = null

    private var resultsMap =
        ConcurrentHashMap<Long, Pair<HandLandmarkerResult?, PoseLandmarkerResult?>>()

    fun setupLandmarker() {
        try {
            setupHandLandmarker()
            setupPoseLandmarker()
        } catch (e: Exception) {
            landmarkerListener.onLandmarkerError(e.message ?: "Landmarker: An unknown error has occurred")
        }
    }

    fun clear() {
        handLandmarker?.close()
        handLandmarker = null
        poseLandmarker?.close()
        poseLandmarker = null
        resultsMap.clear()
    }

    fun recognizeLiveStream(
        imageProxy: ImageProxy,
    ) {
        val timeStamp = SystemClock.uptimeMillis()

        if (handLandmarker == null || poseLandmarker == null) {
            imageProxy.close()
            return
        }

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        handLandmarker?.detectAsync(mpImage, timeStamp)
        poseLandmarker?.detectAsync(mpImage, timeStamp)
    }

    fun isClosed(): Boolean {
        return handLandmarker == null || poseLandmarker == null
    }

    private fun setupHandLandmarker() {
        val modelBuffer =
            loadModelFile(Application.dataDirPath + File.separator + "model" + File.separator + "hand_landmarker.task")

        val gpuBaseOptions =
            BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetBuffer(modelBuffer).build()

        val cpuBaseOptions =
            BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetBuffer(modelBuffer).build()

        val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder().setNumHands(NUM_HANDS)
            .setMinHandDetectionConfidence(MIN_HAND_DETECTION_CONFIDENCE)
            .setMinHandPresenceConfidence(MIN_HAND_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_HAND_TRACKING_CONFIDENCE)
            .setRunningMode(RunningMode.LIVE_STREAM).setResultListener(this::handLivestreamResult)
            .setErrorListener(this::handLivestreamError)

        handLandmarker = if (SharedState.useHwAcceleration) {
            try {
                HandLandmarker.createFromOptions(
                    context, optionsBuilder.setBaseOptions(gpuBaseOptions).build()
                )
            } catch (_: Exception) {
                landmarkerListener.onLandmarkerError("Cannot use GPU delegate for HandLandmarker. Falling back to CPU...")

                HandLandmarker.createFromOptions(
                    context, optionsBuilder.setBaseOptions(cpuBaseOptions).build()
                )
            }
        } else {
            HandLandmarker.createFromOptions(
                context, optionsBuilder.setBaseOptions(cpuBaseOptions).build()
            )
        }
    }

    private fun setupPoseLandmarker() {
        val modelBuffer =
            loadModelFile(Application.dataDirPath + File.separator + "model" + File.separator + "pose_landmarker_lite.task")

        val gpuBaseOptions =
            BaseOptions.builder().setDelegate(Delegate.GPU).setModelAssetBuffer(modelBuffer).build()

        val cpuBaseOptions =
            BaseOptions.builder().setDelegate(Delegate.CPU).setModelAssetBuffer(modelBuffer).build()

        val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setMinPoseDetectionConfidence(MIN_POSE_DETECTION_CONFIDENCE)
            .setMinPosePresenceConfidence(MIN_POSE_PRESENCE_CONFIDENCE)
            .setMinTrackingConfidence(MIN_POSE_TRACKING_CONFIDENCE)
            .setRunningMode(RunningMode.LIVE_STREAM).setResultListener(this::poseLivestreamResult)
            .setErrorListener(this::poseLivestreamError)

        poseLandmarker = if (SharedState.useHwAcceleration) {
            try {
                PoseLandmarker.createFromOptions(
                    context, optionsBuilder.setBaseOptions(gpuBaseOptions).build()
                )
            } catch (_: Exception) {
                landmarkerListener.onLandmarkerError("Cannot use GPU delegate for PoseLandmarker. Falling back to CPU...")

                PoseLandmarker.createFromOptions(
                    context, optionsBuilder.setBaseOptions(cpuBaseOptions).build()
                )
            }
        } else {
            PoseLandmarker.createFromOptions(
                context, optionsBuilder.setBaseOptions(cpuBaseOptions).build()
            )
        }
    }

    private fun handLivestreamResult(
        result: HandLandmarkerResult, input: MPImage
    ) {
        val timestamp = result.timestampMs()
        val value = resultsMap.putIfAbsent(timestamp, Pair(result, null))

        if (value != null) {
            resultsMap[timestamp] = Pair(result, value.second)
            processResults(timestamp, input)
        }
    }

    private fun poseLivestreamResult(
        result: PoseLandmarkerResult, input: MPImage
    ) {
        val timestamp = result.timestampMs()
        val value = resultsMap.putIfAbsent(timestamp, Pair(null, result))

        if (value != null) {
            resultsMap[timestamp] = Pair(value.first, result)
            processResults(timestamp, input)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun processResults(timestamp: Long, image: MPImage) {
        val (handResult, poseResult) = resultsMap.remove(timestamp)!!

        val (leftHandLandmarks, rightHandLandmarks) = processHands(
            handResult!!, false
        ) as Pair<List<NormalizedLandmark>?, List<NormalizedLandmark>?>

        val (leftHandWorldLandmarks, rightHandWorldLandmarks) = processHands(
            handResult, true
        ) as Pair<List<Landmark>?, List<Landmark>?>

        val poseLandmarks = poseResult!!.landmarks().firstOrNull()
        val poseWorldLandmarks = poseResult.worldLandmarks().firstOrNull()

        val finishTimeMs = SystemClock.uptimeMillis()

        landmarkerListener.onLandmarkerResult(
            Result(
                LandmarkerResult(
                    leftHandLandmarks,
                    leftHandWorldLandmarks,
                    rightHandLandmarks,
                    rightHandWorldLandmarks,
                    poseLandmarks,
                    poseWorldLandmarks
                ), timestamp, finishTimeMs - timestamp, image.width, image.height
            )
        )
    }

    private fun processHands(
        result: HandLandmarkerResult, relativeToHandCenter: Boolean
    ): Pair<List<*>?, List<*>?> {
        // Mediapipe assumes image is is mirrored, i.e., taken with a front-facing/selfie camera with images flipped horizontally.
        // Because of this, the handedness is swapped.
        val leftInTheMirror = "Right"
        val rightInTheMirror = "Left"

        var left: Pair<List<*>, Float>? = null
        var right: Pair<List<*>, Float>? = null

        val resultHandLandmarks =
            if (relativeToHandCenter) result.worldLandmarks() else result.landmarks()

        result.handednesses().zip(resultHandLandmarks) { handedness, handLandmarks ->
            val handednessCategory = handedness[0]

            if (handednessCategory.categoryName() == leftInTheMirror) {
                if (left == null) {
                    left = Pair(handLandmarks, handednessCategory.score())
                } else {
                    if (handednessCategory.score() > left!!.second) {
                        right = left
                        left = Pair(handLandmarks, handednessCategory.score())
                    } else {
                        right = Pair(handLandmarks, handednessCategory.score())
                    }
                }
            }

            if (handednessCategory.categoryName() == rightInTheMirror) {
                if (right == null) {
                    right = Pair(handLandmarks, handednessCategory.score())
                } else {
                    if (handednessCategory.score() > right!!.second) {
                        left = right
                        right = Pair(handLandmarks, handednessCategory.score())
                    } else {
                        left = Pair(handLandmarks, handednessCategory.score())
                    }
                }
            }
        }

        return Pair(left?.first, right?.first)
    }

    private fun handLivestreamError(error: Exception) {
        landmarkerListener.onLandmarkerError(
            error.message ?: "Landmarker: An unknown error has occurred"
        )
    }

    private fun poseLivestreamError(error: Exception) {
        landmarkerListener.onLandmarkerError(
            error.message ?: "Landmarker: An unknown error has occurred"
        )
    }

    data class LandmarkerResult(
        val leftHandLandmarks: List<NormalizedLandmark>?,
        val leftHandWorldLandmarks: List<Landmark>?,
        val rightHandLandmarks: List<NormalizedLandmark>?,
        val rightHandWorldLandmarks: List<Landmark>?,
        val poseLandmarks: List<NormalizedLandmark>?,
        val poseWorldLandmarks: List<Landmark>?
    )

    data class Result(
        val landmarkerResult: LandmarkerResult,
        val frameTimestamp: Long,
        val inferenceTime: Long,
        val imageWidth: Int,
        val imageHeight: Int
    )

    interface LandmarkerListener {
        fun onLandmarkerResult(result: Result)
        fun onLandmarkerError(message: String)
    }
}