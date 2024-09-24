package radu.signlanguageinterpreter.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CheckBox
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.activities.MirrorActivity
import radu.signlanguageinterpreter.ai.Landmarker
import radu.signlanguageinterpreter.ai.Translator
import radu.signlanguageinterpreter.databinding.FragmentCameraBinding
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.helpers.SentenceBuilder
import radu.signlanguageinterpreter.helpers.Speaker
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class CameraFragment : Fragment(), Landmarker.LandmarkerListener, Translator.TranslatorListener {
    private val nullResult = Landmarker.Result(
        Landmarker.LandmarkerResult(
            null, null, null, null, null, null
        ), 0, 0, 0, 0
    )

    private lateinit var activity: Activity
    private var _binding: FragmentCameraBinding? = null
    private val binding
        get() = _binding!!

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var landmarker: Landmarker
    private lateinit var translator: Translator
    private lateinit var speaker: Speaker

    private val predictionQueue = ArrayDeque<Translator.Result>()
    private var toast: Toast? = null
    private var isRunning = false

    override fun onDestroyView() {
        if (this::landmarker.isInitialized) {
            backgroundExecutor.execute {
                landmarker.clear()
            }
        }
        if (this::translator.isInitialized) {
            backgroundExecutor.execute {
                translator.clear()
            }
        }

        _binding = null
        super.onDestroyView()

        backgroundExecutor.shutdown()

        speaker.destroy()

        predictionQueue.clear()
        toast?.cancel()
        toast = null
        SentenceBuilder.clear()

        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)

        activity = requireActivity()

        initBottomSettings()

        binding.recordButton.toggleButton.setOnClickListener {
            if ((it as ToggleButton).isChecked) {
                backgroundExecutor.execute {
                    if (landmarker.isClosed()) {
                        landmarker.setupLandmarker()
                    }

                    if (translator.isClosed()) {
                        translator.setupTranslator()
                    }
                }
                isRunning = true
            } else {
                if (this::landmarker.isInitialized) {
                    backgroundExecutor.execute {
                        landmarker.clear()
                    }
                }
                if (this::translator.isInitialized) {
                    backgroundExecutor.execute {
                        translator.clear()
                    }
                }
                isRunning = false

                val lexemes = predictionQueue.map { result -> result.prediction }
                    .filter { lexeme -> lexeme.isNotEmpty() }
                predictionQueue.clear()

                if (SharedState.sentenceAtEnd.get() && lexemes.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val sentence = SentenceBuilder.buildSentence(lexemes)

                        if (sentence != null) {
                            speaker.speakOut(sentence)
                        } else {
                            SharedState.sentenceAtEnd.set(false)
                            activity.runOnUiThread {
                                binding.bottomSettings.useGpt.isChecked = false

                                Toast.makeText(
                                    requireContext(),
                                    Application.getString(R.string.unknown_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            speaker.speakOut(lexemes.joinToString(" "))
                        }
                    }
                }
            }
        }

        binding.flipButton.setOnClickListener {
            startActivity(
                Intent(
                    activity, MirrorActivity::class.java
                )
            )
            activity.finish()
            activity.setResult(AppCompatActivity.RESULT_OK)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        backgroundExecutor = Executors.newSingleThreadExecutor()

        binding.cameraView.post {
            setUpCamera()
        }

        speaker = Speaker(activity)

        backgroundExecutor.execute {
            landmarker = Landmarker(
                requireContext(),
                this
            )
            translator = Translator(
                requireContext(), this
            )
        }
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider =
            cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()

        preview = Preview.Builder().setResolutionSelector(resolutionSelector)
            .setTargetRotation(binding.cameraView.display.rotation).build()

        imageAnalyzer = ImageAnalysis.Builder().setResolutionSelector(resolutionSelector)
            .setTargetRotation(binding.cameraView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build().also {
                it.setAnalyzer(backgroundExecutor) { image ->
                    landmarker.recognizeLiveStream(image)
                }
            }

        cameraProvider.unbindAll()

        camera = cameraProvider.bindToLifecycle(
            this, cameraSelector, preview, imageAnalyzer
        )

        preview!!.setSurfaceProvider(binding.cameraView.surfaceProvider)
    }

    override fun onLandmarkerResult(result: Landmarker.Result) {
        activity.runOnUiThread {
            if (_binding != null) {
                binding.overlay.setResult(if (SharedState.drawSkeletonOverlay && isRunning) result else nullResult)
                binding.overlay.invalidate()
            }
        }

        try {
            backgroundExecutor.execute {
                translator.addResultToQueue(result)
            }
        } catch (_: RejectedExecutionException) {
        }
    }

    override fun onLandmarkerError(message: String) {
        activity.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onTranslatorResult(result: Translator.Result) {
        if (predictionQueue.isEmpty() || predictionQueue.last().prediction != result.prediction) {
            predictionQueue.add(result)

            activity.runOnUiThread {
                binding.bottomSettings.fps.text =
                    "FPS: ${(1000f / (result.startTimestamp - result.frameTimestamp + result.inferenceTime)).roundToInt()}"
            }

            if (SharedState.sentenceAtEnd.get()) {
                activity.runOnUiThread {
                    toast?.cancel()
                    toast = Toast.makeText(
                        requireContext(), result.prediction, Toast.LENGTH_SHORT
                    )
                    toast!!.show()
                }
            } else {
                speaker.speakOut(result.prediction)
            }
        }
    }

    override fun onTranslatorError(message: String) {
        activity.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initBottomSettings() {
        binding.bottomSettings.bottomSettingsContainer.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    binding.bottomSettings.bottomSettingsContainer.viewTreeObserver.removeOnGlobalLayoutListener(
                        this
                    )
                    val behavior = BottomSheetBehavior.from(binding.bottomSettings.root)
                    val hidden = binding.bottomSettings.bottomSettingsInnerContainer.getChildAt(1)
                    val peekHeight = hidden.top

                    val params = CoordinatorLayout.LayoutParams(
                        CoordinatorLayout.LayoutParams.MATCH_PARENT,
                        CoordinatorLayout.LayoutParams.MATCH_PARENT
                    )
                    params.setMargins(0, 0, 0, peekHeight)
                    binding.surfaceViewContainer.layoutParams = params

                    behavior.peekHeight = peekHeight
                }
            })


        binding.bottomSettings.useHwAcc.setOnClickListener {
            val checked = (it as CheckBox).isChecked

            SharedState.useHwAcceleration = checked

            backgroundExecutor.execute {
                if (!landmarker.isClosed()) {
                    landmarker.clear()
                    landmarker.setupLandmarker()
                }
            }
        }

        binding.bottomSettings.useGpt.isChecked = SharedState.sentenceAtEnd.get()

        binding.bottomSettings.useGpt.setOnClickListener {
            SharedState.sentenceAtEnd.set((it as CheckBox).isChecked)
        }


        binding.bottomSettings.showOverlay.setOnClickListener {
            SharedState.drawSkeletonOverlay = (it as CheckBox).isChecked
        }
    }
}