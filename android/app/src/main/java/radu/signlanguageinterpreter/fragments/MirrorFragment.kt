package radu.signlanguageinterpreter.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CheckBox
import android.widget.Toast
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
import radu.signlanguageinterpreter.activities.CameraActivity
import radu.signlanguageinterpreter.ai.Landmarker
import radu.signlanguageinterpreter.ai.Translator
import radu.signlanguageinterpreter.databinding.FragmentMirrorBinding
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.helpers.SentenceBuilder
import radu.signlanguageinterpreter.helpers.Speaker
import java.util.LinkedList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

@Suppress("PrivatePropertyName")
class MirrorFragment : Fragment(), Landmarker.LandmarkerListener, Translator.TranslatorListener {
    private val NS_TO_REGISTER_ACTION = 500_000_000

    private val nullResult = Landmarker.Result(
        Landmarker.LandmarkerResult(
            null, null, null, null, null, null
        ), 0, 0, 0, 0
    )

    private lateinit var activity: Activity
    private var _binding: FragmentMirrorBinding? = null
    private val binding
        get() = _binding!!
    private var width = 0
    private var height = 0

    private lateinit var recordButtonBb: MirroredBoundingBox
    private lateinit var correctTextViewBb: MirroredBoundingBox
    private lateinit var wrongTextViewBb: MirroredBoundingBox
    private lateinit var choiceOneTextViewBb: MirroredBoundingBox
    private lateinit var choiceTwoTextViewBb: MirroredBoundingBox
    private lateinit var choiceThreeTextViewBb: MirroredBoundingBox

    private var lastOnRecordButton: Long? = null
    private var lastOnCorrectTextView: Long? = null
    private var lastOnWrongTextView: Long? = null
    private var lastOnChoiceOneTextView: Long? = null
    private var lastOnChoiceTwoTextView: Long? = null
    private var lastOnChoiceThreeTextView: Long? = null

    private var debounceRecordButton = false

    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var landmarker: Landmarker
    private lateinit var translator: Translator
    private lateinit var speaker: Speaker

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    private var currentPrediction: String? = null
    private val predictionsList = LinkedList<String>()
    private val sentenceChoicesList = mutableListOf<String>()
    private var recording = false

    private fun getViewBb(v: Int): MirroredBoundingBox {
        val view = binding.root.findViewById<View>(v)
        val location = IntArray(2)
        view.getLocationOnScreen(location)

        return MirroredBoundingBox(
            location[0], location[1], location[0] + view.width, location[1] + view.height
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMirrorBinding.inflate(inflater, container, false)

        val displayMetrics = Resources.getSystem().displayMetrics
        width = displayMetrics.widthPixels
        height = displayMetrics.heightPixels

        activity = requireActivity()

        initBottomSettings()

        binding.recordButton.toggleButton.isEnabled = false

        binding.flipButton.setOnClickListener {
            startActivity(
                Intent(
                    activity, CameraActivity::class.java
                )
            )
            activity.finish()
            activity.setResult(AppCompatActivity.RESULT_OK)
        }

        binding.recordButton.toggleButton.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                recordButtonBb = getViewBb(R.id.record_button)
            }
        })

        binding.correctTextView.viewTreeObserver.addOnGlobalLayoutListener {
            correctTextViewBb = getViewBb(R.id.correct_text_view)
        }

        binding.wrongTextView.viewTreeObserver.addOnGlobalLayoutListener {
            wrongTextViewBb = getViewBb(R.id.wrong_text_view)
        }

        binding.choiceOneTextView.viewTreeObserver.addOnGlobalLayoutListener {
            choiceOneTextViewBb = getViewBb(R.id.choice_one_text_view)
        }

        binding.choiceTwoTextView.viewTreeObserver.addOnGlobalLayoutListener {
            choiceTwoTextViewBb = getViewBb(R.id.choice_two_text_view)
        }

        binding.choiceThreeTextView.viewTreeObserver.addOnGlobalLayoutListener {
            choiceThreeTextViewBb = getViewBb(R.id.choice_three_text_view)
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
                requireContext(), this
            )
            translator = Translator(
                requireContext(), this
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (landmarker.isClosed()) {
            backgroundExecutor.execute {
                landmarker.setupLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::landmarker.isInitialized) {
            backgroundExecutor.execute {
                landmarker.clear()
            }
        }
    }

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

        SentenceBuilder.clear()

        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
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
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()

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

    override fun onLandmarkerResult(result: Landmarker.Result) = activity.runOnUiThread {
        activity.runOnUiThread {
            if (_binding != null) {
                binding.overlay.setResult(
                    if (SharedState.drawSkeletonOverlay) result else nullResult, true
                )
                binding.overlay.invalidate()
            }
        }

        val indexFingerTip = result.landmarkerResult.rightHandLandmarks?.get(8)

        if (indexFingerTip != null) {
            val scaleFactor = max(width * 1f / result.imageWidth, height * 1f / result.imageHeight)
            val x = ((1 - indexFingerTip.x()) * result.imageWidth * scaleFactor).roundToInt()
            val y = (indexFingerTip.y() * result.imageHeight * scaleFactor).roundToInt()

            val instant = System.nanoTime()

            if (recordButtonBb.contains(x, y)) {
                if (debounceRecordButton) {
                    return@runOnUiThread
                }

                if (lastOnRecordButton == null) {
                    lastOnRecordButton = instant
                    return@runOnUiThread
                }

                if (instant - lastOnRecordButton!! >= NS_TO_REGISTER_ACTION) {
                    onRecordToggled()
                    debounceRecordButton = true

                    lastOnRecordButton = null
                    lastOnCorrectTextView = null
                    lastOnWrongTextView = null
                    lastOnChoiceOneTextView = null
                    lastOnChoiceTwoTextView = null
                    lastOnChoiceThreeTextView = null

                    return@runOnUiThread
                }
            } else {
                debounceRecordButton = false
                lastOnRecordButton = null
            }

            if (currentPrediction != null) {
                if (correctTextViewBb.contains(x, y)) {
                    if (lastOnCorrectTextView == null) {
                        lastOnCorrectTextView = instant
                        return@runOnUiThread
                    }

                    if (instant - lastOnCorrectTextView!! >= NS_TO_REGISTER_ACTION) {
                        onCorrectPressed()

                        lastOnRecordButton = null
                        lastOnCorrectTextView = null
                        lastOnWrongTextView = null
                        lastOnChoiceOneTextView = null
                        lastOnChoiceTwoTextView = null
                        lastOnChoiceThreeTextView = null

                        return@runOnUiThread
                    }
                } else {
                    lastOnCorrectTextView = null
                }

                if (wrongTextViewBb.contains(x, y)) {
                    if (lastOnWrongTextView == null) {
                        lastOnWrongTextView = instant
                        return@runOnUiThread
                    }

                    if (instant - lastOnWrongTextView!! >= NS_TO_REGISTER_ACTION) {
                        onWrongPressed()

                        lastOnRecordButton = null
                        lastOnCorrectTextView = null
                        lastOnWrongTextView = null
                        lastOnChoiceOneTextView = null
                        lastOnChoiceTwoTextView = null
                        lastOnChoiceThreeTextView = null

                        return@runOnUiThread
                    }
                } else {
                    lastOnWrongTextView = null
                }
            }

            if (sentenceChoicesList.size >= 1) {
                if (choiceOneTextViewBb.contains(x, y)) {
                    if (lastOnChoiceOneTextView == null) {
                        lastOnChoiceOneTextView = instant
                        return@runOnUiThread
                    }

                    if (instant - lastOnChoiceOneTextView!! >= NS_TO_REGISTER_ACTION) {
                        onChoiceSelected(1)

                        lastOnRecordButton = null
                        lastOnCorrectTextView = null
                        lastOnWrongTextView = null
                        lastOnChoiceOneTextView = null
                        lastOnChoiceTwoTextView = null
                        lastOnChoiceThreeTextView = null

                        return@runOnUiThread
                    }
                } else {
                    lastOnChoiceOneTextView = null
                }
            }

            if (sentenceChoicesList.size >= 2) {
                if (choiceTwoTextViewBb.contains(x, y)) {
                    if (lastOnChoiceTwoTextView == null) {
                        lastOnChoiceTwoTextView = instant
                        return@runOnUiThread
                    }

                    if (instant - lastOnChoiceTwoTextView!! >= NS_TO_REGISTER_ACTION) {
                        onChoiceSelected(2)

                        lastOnRecordButton = null
                        lastOnCorrectTextView = null
                        lastOnWrongTextView = null
                        lastOnChoiceOneTextView = null
                        lastOnChoiceTwoTextView = null
                        lastOnChoiceThreeTextView = null

                        return@runOnUiThread
                    }
                } else {
                    lastOnChoiceTwoTextView = null
                }
            }

            if (sentenceChoicesList.size >= 3) {
                if (choiceThreeTextViewBb.contains(x, y)) {
                    if (lastOnChoiceThreeTextView == null) {
                        lastOnChoiceThreeTextView = instant
                        return@runOnUiThread
                    }

                    if (instant - lastOnChoiceThreeTextView!! >= NS_TO_REGISTER_ACTION) {
                        onChoiceSelected(3)

                        lastOnRecordButton = null
                        lastOnCorrectTextView = null
                        lastOnWrongTextView = null
                        lastOnChoiceOneTextView = null
                        lastOnChoiceTwoTextView = null
                        lastOnChoiceThreeTextView = null

                        return@runOnUiThread
                    }
                } else {
                    lastOnChoiceThreeTextView = null
                }
            }
        }

        if (currentPrediction != null || sentenceChoicesList.isNotEmpty()) {
            return@runOnUiThread
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
    override fun onTranslatorResult(result: Translator.Result) = activity.runOnUiThread {
        activity.runOnUiThread {
            binding.bottomSettings.fps.text =
                "FPS: ${(1000f / (result.startTimestamp - result.frameTimestamp + result.inferenceTime)).roundToInt()}"
        }

        currentPrediction = result.prediction
        binding.predictedWordTextView.text = currentPrediction

        binding.correctTextView.visibility = View.VISIBLE
        binding.wrongTextView.visibility = View.VISIBLE
        binding.predictedWordTextView.visibility = View.VISIBLE
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

    private fun onRecordToggled() {
        recording = !recording
        binding.recordButton.toggleButton.toggle()

        if (recording) {
            backgroundExecutor.execute {
                if (translator.isClosed()) {
                    translator.setupTranslator()
                }
            }
            recording = true

            clear()
        } else {
            if (this::translator.isInitialized) {
                backgroundExecutor.execute {
                    translator.clear()
                }
            }
            recording = false

            val lexemes = predictionsList.filter { it.isNotEmpty() }
            clear()

            if (lexemes.isEmpty()) {
                return
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val sentences = SentenceBuilder.buildSentenceVariants(lexemes)

                if (sentences != null) {
                    sentenceChoicesList.addAll(sentences)
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            requireContext(),
                            Application.getString(R.string.unknown_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    sentenceChoicesList.add(lexemes.joinToString(" "))
                }

                activity.runOnUiThread {
                    if (sentenceChoicesList.size >= 1) {
                        binding.choiceOneTextView.text = sentenceChoicesList[0]
                        binding.choiceOneTextView.visibility = View.VISIBLE
                    }

                    if (sentenceChoicesList.size >= 2) {
                        binding.choiceTwoTextView.text = sentenceChoicesList[1]
                        binding.choiceTwoTextView.visibility = View.VISIBLE
                    }

                    if (sentenceChoicesList.size >= 3) {
                        binding.choiceThreeTextView.text = sentenceChoicesList[2]
                        binding.choiceThreeTextView.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun onCorrectPressed() {
        translator.clearQueue()

        if (SharedState.sentenceAtEnd.get()) {
            predictionsList.add(currentPrediction!!)
        } else {
            speaker.speakOut(currentPrediction!!)
        }
        currentPrediction = null
        binding.correctTextView.visibility = View.GONE
        binding.wrongTextView.visibility = View.GONE
        binding.predictedWordTextView.visibility = View.GONE
    }

    private fun onWrongPressed() {
        translator.clearQueue()

        currentPrediction = null
        binding.correctTextView.visibility = View.GONE
        binding.wrongTextView.visibility = View.GONE
        binding.predictedWordTextView.visibility = View.GONE

    }

    private fun onChoiceSelected(choice: Int) {
        val sentence = sentenceChoicesList[choice - 1]

        sentenceChoicesList.clear()
        binding.choiceOneTextView.visibility = View.GONE
        binding.choiceTwoTextView.visibility = View.GONE
        binding.choiceThreeTextView.visibility = View.GONE

        speaker.speakOut(sentence)
    }

    private fun clear() {
        binding.correctTextView.visibility = View.GONE
        binding.wrongTextView.visibility = View.GONE
        binding.predictedWordTextView.visibility = View.GONE
        binding.choiceOneTextView.visibility = View.GONE
        binding.choiceTwoTextView.visibility = View.GONE
        binding.choiceThreeTextView.visibility = View.GONE

        currentPrediction = null
        predictionsList.clear()
        sentenceChoicesList.clear()
    }

    private class MirroredBoundingBox(
        private val left: Int, private val top: Int, private val right: Int, private val bottom: Int
    ) {
        fun contains(x: Int, y: Int): Boolean {
            return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom
        }
    }
}