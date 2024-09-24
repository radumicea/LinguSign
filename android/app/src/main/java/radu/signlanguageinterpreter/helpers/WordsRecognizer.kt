package radu.signlanguageinterpreter.helpers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.globals.postDelayed
import radu.signlanguageinterpreter.globals.showDialogAndExit
import radu.signlanguageinterpreter.io.HttpClient
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class WordsRecognizer(private val activity: Activity) {
    private val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    private val networkError = AtomicBoolean(false)
    private val requestCounter = AtomicInteger(0)
    private val delayMillis = 1000L
    private val delayHandler = Handler.createAsync(Looper.getMainLooper())
    private val words = LinkedList<String>()
    private val lock = ReentrantLock(true)
    private val processThenClearWords = Runnable {
        lock.lock()
        try {
            processWords(words.toList(), true)
            words.clear()
        } finally {
            lock.unlock()
        }
    }

    private var endResultRunnable: Runnable? = null
    private var recognizer: SpeechRecognizer? = null

    fun startRecognition() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(Application.context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(bundle: Bundle) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(v: Float) {}

                override fun onBufferReceived(bytes: ByteArray?) {}

                override fun onEndOfSpeech() {
                    stopRecognition()
                    postDelayed({
                        startRecognition()
                    }, 100)
                }

                override fun onError(i: Int) {
                    if (i != SpeechRecognizer.ERROR_CLIENT) {
                        stopRecognition()
                        postDelayed({
                            startRecognition()
                        }, 100)
                    }
                }

                override fun onResults(bundle: Bundle) {}

                override fun onPartialResults(result: Bundle) {
                    val data = result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val sentence = data?.get(0) ?: ""

                    if (sentence.isBlank()) {
                        return
                    }

                    val lastWord = sentence.split(' ').last().lowercase()

                    lock.lock()
                    try {
                        if (lastWord.isEmpty() || lastWord.equals(words.lastOrNull(), true)) {
                            return
                        }

                        words.add(lastWord)

                        if (endResultRunnable != null) {
                            delayHandler.removeCallbacks(endResultRunnable!!)
                            if (words.size >= 3) {
                                processWords(words.toList(), false)
                            }
                        }

                        endResultRunnable = processThenClearWords

                        delayHandler.postDelayed(endResultRunnable!!, delayMillis)
                    } finally {
                        lock.unlock()
                    }
                }

                override fun onEvent(i: Int, bundle: Bundle) {}
            })
        }

        recognizer?.startListening(intent)
    }

    fun stopRecognition() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun processWords(currentWords: List<String>, isEnd: Boolean) =
        CoroutineScope(Dispatchers.IO).launch {
            if (networkError.get() || currentWords.isEmpty() || currentWords.all { it.isEmpty() }) {
                return@launch
            }

            Log.i("currentWords", currentWords.joinToString(" "))

            val requestId = requestCounter.getAndIncrement()
            val result = HttpClient.post(
                "nlp/sentence", object : TypeToken<List<List<String>>>() {}, mapOf(
                    "sentence" to currentWords.joinToString(" "), "isEnd" to isEnd
                )
            )

            if (result.isFailure) {
                networkError.set(true)
                activity.runOnUiThread {
                    stopRecognition()
                }
                activity.showDialogAndExit()
                return@launch
            }

            SharedState.wordsQueue.addAll(requestId, result.getOrThrow().flatten())
        }
}
