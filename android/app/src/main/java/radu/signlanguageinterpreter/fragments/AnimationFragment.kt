package radu.signlanguageinterpreter.fragments

//noinspection SuspiciousImport
import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.ToggleButton
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.databinding.FragmentAnimationBinding
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.helpers.CharRenderer
import radu.signlanguageinterpreter.helpers.WordsRecognizer
import radu.signlanguageinterpreter.io.LandmarksLoader
import java.io.File

class AnimationFragment : Fragment(), SurfaceHolder.Callback {
    private lateinit var binding: FragmentAnimationBinding
    private lateinit var surfaceView: SurfaceView
    private lateinit var charRenderer: CharRenderer
    private lateinit var wordsRecognizer: WordsRecognizer
    private lateinit var activity: Activity
    private lateinit var audioManager: AudioManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentAnimationBinding.inflate(layoutInflater)

        activity = requireActivity()
        audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        wordsRecognizer = WordsRecognizer(activity)

        binding.recordButton.toggleButton.setOnClickListener {
            if ((it as ToggleButton).isChecked) {
                wordsRecognizer.startRecognition()
            } else {
                wordsRecognizer.stopRecognition()
            }
        }

        initBottomSettings()

        surfaceView = binding.charSurfaceView
        surfaceView.holder.addCallback(this)

        return binding.root
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val charPath =
            Application.dataDirPath + File.separator + "char" + File.separator + SharedState.selectedCharName.lowercase() + ".glb"
        charRenderer = CharRenderer(charPath, "lightroom_14b", surfaceView, this)
        charRenderer.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        charRenderer.stop()
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


        with(binding.bottomSettings.seekBarSpeed) {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    SharedState.selectedAnimationSpeed = progress / 10f
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                }

            })
            progress = (SharedState.selectedAnimationSpeed * 10).toInt()
        }


        with(binding.bottomSettings.seekBarSmoothness) {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                var progressChangedValue = 0

                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    progressChangedValue = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (progressChangedValue > 0) {
                        SharedState.selectedWindowSize = progressChangedValue
                        LandmarksLoader.reload()
                    }
                }

            })
            progress = SharedState.selectedWindowSize
        }


        val actv = binding.bottomSettings.actvInterpreter

        val options = File(Application.dataDirPath + File.separator + "char").listFiles { f -> f.name.endsWith(".glb") }!!
            .map { file -> file.name.replace(".glb", "").replaceFirstChar { it.uppercase() } }.toTypedArray()

        val adapter = ArrayAdapter(activity, R.layout.simple_dropdown_item_1line, options)

        actv.setAdapter(adapter)

        actv.setText(SharedState.selectedCharName, false)

        actv.setOnItemClickListener { parent, _, position, _ ->
            val selectedCharName = parent.getItemAtPosition(position).toString()

            if (selectedCharName == SharedState.selectedCharName) {
                return@setOnItemClickListener
            }

            SharedState.selectedCharName = selectedCharName
            val newCharPath =
                Application.dataDirPath + File.separator + "char" + File.separator + SharedState.selectedCharName.lowercase() + ".glb"
            charRenderer.stop()
            charRenderer.changeChar(newCharPath)
            charRenderer.start()
        }
    }

    override fun onResume() {
        super.onResume()
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.ADJUST_MUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun onPause() {
        super.onPause()
        wordsRecognizer.stopRecognition()
        audioManager.adjustStreamVolume(
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.ADJUST_UNMUTE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    @SuppressLint("SetTextI18n")
    fun updateFps(fps: Int) {
        binding.bottomSettings.fps.text = "FPS: $fps"
    }
}