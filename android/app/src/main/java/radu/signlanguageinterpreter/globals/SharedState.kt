package radu.signlanguageinterpreter.globals

import androidx.lifecycle.MutableLiveData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object SharedState {
    // Home - update
    val updating = AtomicBoolean()
    val updatingObservable = MutableLiveData<Boolean>()
    val totalDownloadCount = AtomicInteger()
    val downloadProgress = AtomicInteger()
    val downloadProgressObservable = MutableLiveData<Int>()


    // Animation - words recognizer
    val wordsQueue = WordsQueue()

    // Animation - current landmark fps
    var currentLandmarkFps = 30

    // Animation - bottom settings
    var selectedCharName = DEFAULT_CHARACTER
    var selectedWindowSize = 3
    var selectedAnimationSpeed = 1f


    // Camera - bottom settings
    var useHwAcceleration = true
    var sentenceAtEnd = AtomicBoolean(false)
    var drawSkeletonOverlay = true


    class WordsQueue {
        private var counter = 0
        private var index = 0
        private val map = ConcurrentHashMap<Int, List<String>>()

        // Asynchronously called
        fun addAll(id: Int, words: List<String>) {
            map[id] = words
        }

        // Synchronously called by LandmarksLoader by either UI or Choreographer.FrameCallback
        fun poll(): String? {
            val value = map[counter] ?: return null

            if (index >= value.size) {
                counter++
                index = 0
                return poll()
            }

            val result = value[index]
            index++
            return result
        }

        fun peek(): String? {
            val value = map[counter]

            if (value == null || index >= value.size) {
                return null
            }

            return value[index]
        }
    }
}