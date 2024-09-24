package radu.signlanguageinterpreter.ai

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tflite.java.TfLite
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.jetbrains.kotlinx.multik.api.linalg.norm
import org.jetbrains.kotlinx.multik.api.linspace
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.all
import org.jetbrains.kotlinx.multik.ndarray.operations.div
import org.jetbrains.kotlinx.multik.ndarray.operations.forEach
import org.jetbrains.kotlinx.multik.ndarray.operations.minus
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.InterpreterApi
import org.tensorflow.lite.flex.FlexDelegate
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.loadModelFile
import radu.signlanguageinterpreter.helpers.Skeleton
import java.io.File
import java.io.FileReader
import java.util.concurrent.locks.ReentrantLock

@Suppress("PrivatePropertyName", "SpellCheckingInspection")
class Translator(private val context: Context, private val translatorListener: TranslatorListener) {
    private val MIN_UNCHANGED_MS = 600
    private val MOSTLY_EMPTY_PERCENTAGE = 0.7f
    private val NO_HAND_ARRAY = Array(21) { FloatArray(3) { 0f } }
    private val NO_HAND_D2_ARRAY = mk.ndarray(NO_HAND_ARRAY)
    private val NO_POSE_ARRAY = Array(33) { FloatArray(3) { 0f } }
    private val NO_POSE_D2_ARRAY = mk.ndarray(NO_POSE_ARRAY)
    private val NO_ARM_D2_ARRAY = mk.ndarray(Array(3) { FloatArray(3) { 0f } })
    private val NUM_FRAMES = 15
    private val NUM_HAND_FLOATS = 21 * 3
    private val ONE_SECOND_MS = 1_000
    private val THRESHOLD = 0.9

    private val LEFT_ARM_INDEXES = intArrayOf(11, 13, 15)
    private val RIGHT_ARM_INDEXES = intArrayOf(12, 14, 16)

    private val lock = ReentrantLock(true)

    private val skeleton = Skeleton.DEFAULT
    private val featuresQueue = ArrayDeque<Pair<List<Float>, Long>>()
    private var prevPrediction: Pair<String, Long>? = null

    private var gestures: List<String>? = null
    private var interpreter: InterpreterApi? = null

    private var lhPrev = NO_HAND_D2_ARRAY
    private var rhPrev = NO_HAND_D2_ARRAY
    private var posePrev = NO_POSE_D2_ARRAY

    fun setupTranslator() {
        lock.lock()
        try {
            val reader =
                JsonReader(FileReader(Application.dataDirPath + File.separator + "class" + File.separator + "gestures.json"))
            gestures = Gson().fromJson(reader, object : TypeToken<List<String>>() {})

            TfLite.initialize(context).continueWith {
                try {
                    val options = Interpreter.Options().addDelegate(FlexDelegate())
                    val modelBuffer =
                        loadModelFile(Application.dataDirPath + File.separator + "model" + File.separator + "gestures.tflite")
                    interpreter = InterpreterApi.create(modelBuffer, options)
                    interpreter!!.allocateTensors()
                } catch (e: Exception) {
                    translatorListener.onTranslatorError(
                        e.message ?: "Translator: An unknown error has occurred"
                    )
                }
            }.addOnFailureListener {
                translatorListener.onTranslatorError(
                    it.message ?: "Translator: An unknown error has occurred"
                )
            }
        } finally {
            lock.unlock()
        }
    }

    fun clear() {
        lock.lock()
        try {
            interpreter?.close()
            interpreter = null
            featuresQueue.clear()
            prevPrediction = null
            lhPrev = NO_HAND_D2_ARRAY
            rhPrev = NO_HAND_D2_ARRAY
            posePrev = NO_POSE_D2_ARRAY
        } finally {
            lock.unlock()
        }
    }

    fun clearQueue() {
        featuresQueue.clear()
    }

    fun addResultToQueue(result: Landmarker.Result) {
        val start = SystemClock.uptimeMillis()

        lock.lock()
        try {
            if (interpreter == null) {
                return
            }

            val timestamp = result.frameTimestamp

            val features = extractFeatures(result.landmarkerResult)

            featuresQueue.add(Pair(features, timestamp))

            var predictionScore = 0f
            var predictedGesture = ""

            if (timestamp - featuresQueue.first().second >= ONE_SECOND_MS) {
                while (timestamp - featuresQueue.first().second >= ONE_SECOND_MS) {
                    featuresQueue.removeFirst()
                }

                val input = featuresQueue.map { it.first }

                if (mostlyEmpty(input)) {
                    Log.i("i", "empty")
                    featuresQueue.clear()
                    prevPrediction = null
                    lhPrev = NO_HAND_D2_ARRAY
                    rhPrev = NO_HAND_D2_ARRAY
                    posePrev = NO_POSE_D2_ARRAY
                } else {
                    val predictionResult = predict(duplicateSample(input))
                    predictionScore = predictionResult.max()
                    predictedGesture =
                        gestures!![predictionResult.withIndex().maxBy { it.value }.index]
                    Log.i(predictedGesture, predictionScore.toString())
                }
            }

            if (predictionScore >= THRESHOLD) {
                if (prevPrediction == null || prevPrediction!!.first != predictedGesture) {
                    prevPrediction = Pair(predictedGesture, timestamp)
                } else {
                    if (timestamp - prevPrediction!!.second >= MIN_UNCHANGED_MS) {
                        val stop = SystemClock.uptimeMillis()

                        translatorListener.onTranslatorResult(
                            Result(
                                predictedGesture,
                                predictionScore,
                                timestamp,
                                start,
                                stop - start
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            translatorListener.onTranslatorError(
                e.message ?: "Translator: An unknown error has occurred"
            )
        } finally {
            lock.unlock()
        }
    }

    fun isClosed(): Boolean {
        return interpreter == null
    }

    private fun extractFeatures(result: Landmarker.LandmarkerResult): List<Float> {
        val features = ArrayList<Float>()

        addHandWorldToFeatures(result.leftHandWorldLandmarks, features)
        addHandWorldToFeatures(result.rightHandWorldLandmarks, features)
        addArmWorldToFeatures(result.poseWorldLandmarks, true, features)
        addArmWorldToFeatures(result.poseWorldLandmarks, false, features)

        val lhCurr = landmarksToArray(result.leftHandLandmarks, NO_HAND_ARRAY)
        val lhCurrD2Array = mk.ndarray(lhCurr)

        val rhCurr = landmarksToArray(result.rightHandLandmarks, NO_HAND_ARRAY)
        val rhCurrD2Array = mk.ndarray(rhCurr)

        val poseCurr = landmarksToArray(result.poseLandmarks, NO_POSE_ARRAY)
        val poseCurrD2Array = mk.ndarray(poseCurr)

        var displacement = getHandNormalizedDisplacement(lhPrev, lhCurrD2Array)
        displacement.forEach {
            features.add(it)
        }
        lhPrev = lhCurrD2Array

        displacement = getHandNormalizedDisplacement(rhPrev, rhCurrD2Array)
        displacement.forEach {
            features.add(it)
        }
        rhPrev = rhCurrD2Array

        displacement = getPoseNormalizedDisplacement(posePrev, poseCurrD2Array)
        LEFT_ARM_INDEXES.forEach { idx ->
            displacement[idx].forEach {
                features.add(it)
            }
        }
        RIGHT_ARM_INDEXES.forEach { idx ->
            displacement[idx].forEach {
                features.add(it)
            }
        }
        posePrev = poseCurrD2Array

        val rotations = skeleton.getBonesRotationsArray(
            mapOf(
                "pose" to poseCurr, "left_hand" to lhCurr, "right_hand" to rhCurr
            )
        )

        val leftRotations = rotations.drop(3).take(19)
        leftRotations.forEach {
            it.forEach {
                features.add(it)
            }
        }

        val rightRotations = rotations.drop(22).take(19)
        rightRotations.forEach {
            it.forEach {
                features.add(it)
            }
        }

        return features
    }

    private fun addHandWorldToFeatures(handWorld: List<Landmark>?, features: MutableList<Float>) {
        if (handWorld == null) {
            NO_HAND_D2_ARRAY.forEach {
                features.add(it)
            }
        } else {
            handWorld.forEach {
                features.add(it.x())
                features.add(it.y())
                features.add(it.z())
            }
        }
    }

    private fun addArmWorldToFeatures(
        poseWorld: List<Landmark>?, isLeft: Boolean, features: MutableList<Float>
    ) {
        if (poseWorld == null) {
            NO_ARM_D2_ARRAY.forEach {
                features.add(it)
            }
            return
        }

        val indexes = if (isLeft) {
            LEFT_ARM_INDEXES
        } else {
            RIGHT_ARM_INDEXES
        }

        indexes.forEach { idx ->
            val lm = poseWorld[idx]
            features.add(lm.x())
            features.add(lm.y())
            features.add(lm.z())
        }
    }

    private fun landmarksToArray(
        landmarks: List<NormalizedLandmark>?, default: Array<FloatArray>
    ): Array<FloatArray> {
        if (landmarks == null) {
            return default
        }

        return Array(landmarks.size) { idx ->
            val it = landmarks[idx]
            floatArrayOf(it.x(), it.y(), it.z())
        }
    }

    private fun getHandNormalizedDisplacement(
        prev: D2Array<Float>, curr: D2Array<Float>
    ): D2Array<Float> {
        if (prev.all { it == 0f }) {
            return prev
        }

        if (curr.all { it == 0f }) {
            return curr
        }

        val wristPrev = prev[0]
        val wristCurr = curr[0]

        return getNormalizedDisplacement(prev, curr, wristPrev[2], wristCurr[2])
    }

    private fun getPoseNormalizedDisplacement(
        prev: D2Array<Float>, curr: D2Array<Float>
    ): D2Array<Float> {
        if (prev.all { it == 0f }) {
            return prev
        }

        if (curr.all { it == 0f }) {
            return curr
        }

        val midHipsPrev = (prev[23] + prev[24]) / 2f
        val midHipsCurr = (curr[23] + curr[24]) / 2f

        return getNormalizedDisplacement(prev, curr, midHipsPrev[2], midHipsCurr[2])
    }

    private fun getNormalizedDisplacement(
        prev: D2Array<Float>, curr: D2Array<Float>, zOriginPrev: Float, zOriginCurr: Float
    ): D2Array<Float> {
        val nPrev = getNormalizedByDepth(prev, zOriginPrev)
        val nCurr = getNormalizedByDepth(curr, zOriginCurr)

        val d = nCurr - nPrev

        if (d.all { it == 0f }) {
            return d
        }

        return d / mk.linalg.norm(d)
    }

    private fun getNormalizedByDepth(v: D2Array<Float>, zOrigin: Float): D2Array<Float> {
        if (zOrigin == 0f) {
            return if (v.shape[0] == 33) {
                NO_POSE_D2_ARRAY
            } else {
                NO_HAND_D2_ARRAY
            }
        }

        return v / zOrigin
    }

    private fun mostlyEmpty(sequence: List<List<Float>>): Boolean {
        val countEmpty = sequence.count { sublist ->
            sublist.drop(NUM_HAND_FLOATS).take(NUM_HAND_FLOATS).all { element ->
                element == 0f
            }
        }

        return countEmpty / sequence.size.toFloat() > MOSTLY_EMPTY_PERCENTAGE
    }

    private fun duplicateSample(sequence: List<List<Float>>): Array<FloatArray> {
        val idx = mk.linspace<Int>(0, sequence.size - 1, NUM_FRAMES).toList()
        return idx.map { sequence[it].toFloatArray() }.toTypedArray()
    }

    private fun predict(input: Array<FloatArray>): FloatArray {
        val output = arrayOf(FloatArray(gestures!!.size))
        interpreter!!.run(arrayOf(input), output)
        return output[0]
    }

    data class Result(
        val prediction: String,
        val score: Float,
        val frameTimestamp: Long,
        val startTimestamp: Long,
        val inferenceTime: Long
    )

    interface TranslatorListener {
        fun onTranslatorResult(result: Result)
        fun onTranslatorError(message: String)
    }
}