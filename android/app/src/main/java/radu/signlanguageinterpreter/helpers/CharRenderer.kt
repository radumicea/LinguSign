package radu.signlanguageinterpreter.helpers

import android.annotation.SuppressLint
import android.view.Choreographer
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.View
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Quaternion
import com.google.android.filament.utils.slerp
import com.google.android.filament.utils.transpose
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.fragments.AnimationFragment
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.io.LandmarksLoader
import java.io.File
import java.io.FileReader
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

@SuppressLint("ClickableViewAccessibility")
class CharRenderer(
    private var charPath: String,
    private val envName: String?,
    surfaceView: SurfaceView,
    fragment: AnimationFragment
) {
    private var skeleton = Skeleton(charPath)

    private lateinit var initRot: Map<String, Quaternion>
    private lateinit var currRot: Map<String, Quaternion>

    private val viewerContent = AutomationEngine.ViewerContent()
    private val modelViewer = ModelViewer(surfaceView)
    private var choreographer = Choreographer.getInstance()

    private val transformManager = modelViewer.engine.transformManager

    private val matBuf = FloatArray(16)

    private val frameCallback = object : Choreographer.FrameCallback {
        private var currQuats: Map<String, Quaternion>? = null
        private var nextQuats: Map<String, Quaternion>? = null
        private var prevTime = 0L
        private var isFirstFrame = true

        private var prevTimeFpsUpdate = 0L
        private var fpsUpdateTime = 0L
        private val fpsList = mutableListOf<Float>()

        private var tsSpeechStopped = 0L
        private val dtInitRot = 1e9f / 3f

        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            if (fpsList.isNotEmpty() && frameTimeNanos - fpsUpdateTime >= 1e9) {
                fragment.updateFps(fpsList.average().roundToInt())
                fpsList.clear()
                fpsUpdateTime = frameTimeNanos
            } else {
                val dtUpdateFps = frameTimeNanos - prevTimeFpsUpdate
                fpsList.add(1e9f / dtUpdateFps)
            }
            prevTimeFpsUpdate = frameTimeNanos

            val dt = frameTimeNanos - prevTime
            val targetDt =
                1e9f / (SharedState.currentLandmarkFps * SharedState.selectedAnimationSpeed)
            val ratio = dt / targetDt

            val quats: Map<String, Quaternion>

            if (isFirstFrame) {
                quats = getQuats(0)

                currQuats = quats
                nextQuats = getQuats(1)

                prevTime = frameTimeNanos
                isFirstFrame = false
            } else if (ratio <= 1) {
                quats = currQuats!!.keys.intersect(nextQuats!!.keys).associateWith { key ->
                    val curr = currQuats!![key]!!
                    val next = nextQuats!![key]!!

                    slerp(curr, next, ratio)
                }
            } else {
                val framesToSkip = ratio.toInt()

                currQuats = if (framesToSkip == 1) {
                    nextQuats
                } else {
                    getQuats(framesToSkip)
                }
                nextQuats = getQuats(framesToSkip + 1)

                quats = currQuats!!.keys.intersect(nextQuats!!.keys).associateWith { key ->
                    val curr = currQuats!![key]!!
                    val next = nextQuats!![key]!!

                    slerp(curr, next, ratio - framesToSkip)
                }

                prevTime += (framesToSkip * targetDt).toLong()
            }

            if (quats.isNotEmpty()) {
                currRot = quats
            }

            quats.keys.forEach { key ->
                val q = quats[key]!!
                rotateBone(key, q)
            }

            modelViewer.animator!!.updateBoneMatrices()

            modelViewer.render(frameTimeNanos)
        }

        private fun getQuats(offset: Int): Map<String, Quaternion> {
            val landmarks = LandmarksLoader.load(offset)

            if (landmarks == null) {
                if (tsSpeechStopped == 0L) {
                    tsSpeechStopped = System.nanoTime()
                }

                val t = min(1f, (System.nanoTime() - tsSpeechStopped) / dtInitRot)

                val quats = currRot.mapValues {
                    slerp(it.value, initRot[it.key]!!, t)
                }
                quats.keys.forEach { key ->
                    val q = quats[key]!!
                    rotateBone(key, q)
                }

                return mapOf()
            }

            tsSpeechStopped = 0L
            return skeleton.getBonesRotations(landmarks)
        }
    }

    init {
        viewerContent.view = modelViewer.view
        viewerContent.sunlight = modelViewer.light
        viewerContent.lightManager = modelViewer.engine.lightManager
        viewerContent.scene = modelViewer.scene
        viewerContent.renderer = modelViewer.renderer

        loadModel()
        loadEnvironment()

        val engine = modelViewer.engine
        val camera = engine.createCamera(engine.entityManager.create())
        camera.setProjection(
            45.0,
            modelViewer.view.viewport.width.toDouble() / modelViewer.view.viewport.height.toDouble(),
            1.0,
            1000.0,
            Camera.Fov.HORIZONTAL
        )
        camera.lookAt(
            0.0, 1.0, 1.75, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0
        )

        val view = modelViewer.view

        view.camera = camera

        // on mobile, better use lower quality color buffer
        view.renderQuality = view.renderQuality.apply {
            hdrColorBuffer = View.QualityLevel.MEDIUM
        }

// dynamic resolution often helps a lot
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            quality = View.QualityLevel.MEDIUM
        }

// MSAA is needed with dynamic resolution MEDIUM
        view.multiSampleAntiAliasingOptions = view.multiSampleAntiAliasingOptions.apply {
            enabled = true
        }

// FXAA is pretty cheap and helps a lot
        view.antiAliasing = View.AntiAliasing.FXAA

// ambient occlusion is the cheapest effect that adds a lot of quality
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = true
        }

// bloom is pretty expensive but adds a fair amount of realism
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
        }
    }

    fun start() {
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        choreographer.removeFrameCallback(frameCallback)
    }

    fun changeChar(newCharPath: String) {
        charPath = newCharPath
        skeleton = Skeleton(charPath)
        loadModel()
    }

    private fun loadModel() {
        val buffer = readFile(File(charPath).inputStream())
        modelViewer.loadModelGlb(buffer)
        initRot = getInitRot()
        currRot = initRot
        initRot.keys.forEach { key ->
            val q = initRot[key]!!
            rotateBone(key, q)
        }
    }

    private fun getInitRot(): Map<String, Quaternion> {
        val path =
            Application.dataDirPath + File.separator + "misc" + File.separator + "init_landmarks.json"
        val reader = FileReader(path)

        val initLandmarks =
            Gson().fromJson(reader, object : TypeToken<Map<String, List<List<Float>>>>() {})
        val initLandmarksArr =
            initLandmarks.mapValues { it.value.map { it.toFloatArray() }.toTypedArray() }

        return skeleton.getBonesRotations(initLandmarksArr)
    }

    private fun loadEnvironment() {
        if (envName == null) {
            return
        }

        val engine = modelViewer.engine
        val scene = modelViewer.scene

        Application.context.assets.open("envs/$envName/${envName}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, readFile(it))
            scene.indirectLight!!.intensity = 30_000.0f
            viewerContent.indirectLight = modelViewer.scene.indirectLight
        }

        Application.context.assets.open("envs/$envName/${envName}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, readFile(it))
        }
    }

    private fun readFile(input: InputStream): ByteBuffer {
        val bytes = ByteArray(input.available())
        input.read(bytes)
        input.close()
        return ByteBuffer.wrap(bytes)
    }

    private fun rotateBone(boneName: String, q: Quaternion) {
        val rot = transpose(q.toMatrix()).toFloatArray()

        modelViewer.asset!!.getFirstEntityByName(boneName).let { entity ->
            val bone = transformManager.getInstance(entity)

            transformManager.getTransform(bone, matBuf)

            // Preserve position
            rot[12] = matBuf[12]
            rot[13] = matBuf[13]
            rot[14] = matBuf[14]

            transformManager.setTransform(bone, rot)
        }
    }
}