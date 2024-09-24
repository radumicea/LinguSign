@file:Suppress("ConvertToStringTemplate")

package radu.signlanguageinterpreter.io

import android.util.Log
import com.chaquo.python.Python
import org.jetbrains.bio.npy.NpyFile
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.SharedState
import java.io.File
import java.nio.file.Paths

object LandmarksLoader {
    private val landmarksPath =
        Application.dataDirPath + File.separator + "landmark" + File.separator

    private val pySaveSmoothedFunction =
        Python.getInstance().getModule("helpers.landmarks_smoother")["save_smoothed"]!!

    private var prevWord: String? = null
    private var word: String? = null
    private var landmarks: Map<String, Array<Array<FloatArray>>>? = null
    private var curFrame = 0
    private var numFrames = 0
    private var accumulatedNumFrames = 0

    fun load(offsetFrames: Int): Map<String, Array<FloatArray>>? {
        curFrame += offsetFrames

        if (landmarks == null || curFrame >= accumulatedNumFrames) {
            val newWord = SharedState.wordsQueue.poll()

            if (newWord == null || newWord == word) {
                curFrame -= offsetFrames
                return null
            }

            word = newWord
            Log.i("word", word!!)

            reload(true)

            prevWord = word
        }

        return landmarks?.mapValues {
            it.value[curFrame - (accumulatedNumFrames - numFrames)]
        }
    }

    fun reload() {
        reload(false)
    }

    private fun reload(newLandmarks: Boolean) {
        try {
            val fps = pySaveSmoothedFunction.call(
                landmarksPath, word, SharedState.selectedWindowSize
            ).toInt()

            // If the current word is actually a letter, part of a bigger word, animate it faster
            if (word!!.length == 1 && (prevWord?.length == 1 || SharedState.wordsQueue.peek()?.length == 1)) {
                SharedState.currentLandmarkFps = (fps * 1.75).toInt()
            } else {
                SharedState.currentLandmarkFps = fps
            }

            val path = landmarksPath + word + "_smooth.npy"
            val npyArray = NpyFile.read(Paths.get(path)).asFloatArray()

            numFrames = npyArray.size / (3 * 33 * 3)
            if (newLandmarks) {
                accumulatedNumFrames += numFrames
            }

            val lms = Array(3) {
                Array(numFrames) {
                    Array(33) {
                        FloatArray(3)
                    }
                }
            }

            for (i in npyArray.indices) {
                val outerIndex = i / (numFrames * 33 * 3)
                val middleIndex = (i % (numFrames * 33 * 3)) / (33 * 3)
                val innerIndex1 = (i % (33 * 3)) / 3
                val innerIndex2 = i % 3

                lms[outerIndex][middleIndex][innerIndex1][innerIndex2] = npyArray[i]
            }

            val pose = lms[0]
            val lh = lms[1]
            val rh = lms[2]

            landmarks = mapOf("pose" to pose, "left_hand" to lh, "right_hand" to rh)
        } catch (_: Exception) {
            landmarks = null
        }
    }
}