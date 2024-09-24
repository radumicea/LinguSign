package radu.signlanguageinterpreter.helpers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

class Speaker(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.forLanguageTag("ro-RO"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                    context, "Could not initialize TTS", Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            Toast.makeText(
                context, "Could not initialize TTS", Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun speakOut(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, null)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}