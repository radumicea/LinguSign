package radu.signlanguageinterpreter.helpers

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.google.android.filament.utils.Quaternion
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.DEFAULT_CHARACTER
import java.io.File

class Skeleton(charPath: String) {
    companion object {
        val DEFAULT: Skeleton

        private val pySkeletonModule = Python.getInstance().getModule("skeleton")
        private val pyCalcRFunction = Python.getInstance().getModule("calc_R")["calc_R"]!!

        private val nullLmQuat = Quaternion(0f, 0f, 0.5f, 0f)

        private val boneIndexesMap: Array<String>

        init {
            val indexesBoneMap =
                pySkeletonModule["_indexes_bone_map"]!!.asMap().entries.associate { (key, value) ->
                        key.toString() to value.toInt()
                    }
            boneIndexesMap = Array(indexesBoneMap.size) { "" }
            indexesBoneMap.entries.forEach { (key, value) ->
                boneIndexesMap[value] = key
            }

            DEFAULT =
                Skeleton(Application.dataDirPath + File.separator + "char" + File.separator + DEFAULT_CHARACTER.lowercase() + ".glb")
        }
    }

    private val pySkeletonClassInstance: PyObject
    private val boneNamesMap: Map<String, String>

    init {
        pySkeletonClassInstance = pySkeletonModule.callAttr("Skeleton", charPath)
        boneNamesMap =
            pySkeletonClassInstance["_bone_names_map"]!!.asMap().entries.associate { (key, value) ->
                    key.toString() to value.toString()
                }
    }

    fun getBonesRotations(landmarks: Map<String, Array<FloatArray>>): Map<String, Quaternion> {
        return pyCalcRFunction.call(
            pySkeletonClassInstance,
            landmarks["pose"]!!,
            landmarks["left_hand"]!!,
            landmarks["right_hand"]!!
        ).toJava(Array<FloatArray>::class.java).mapIndexed { index, value ->
                getMappedName(index) to Quaternion(
                    value[1], value[2], value[3], value[0]
                )
            }.filter {
                it.second != nullLmQuat
            }.toMap()
    }

    fun getBonesRotationsArray(landmarks: Map<String, Array<FloatArray>>): Array<FloatArray> {
        return pyCalcRFunction.call(
            pySkeletonClassInstance,
            landmarks["pose"]!!,
            landmarks["left_hand"]!!,
            landmarks["right_hand"]!!
        ).toJava(Array<FloatArray>::class.java)
    }

    private fun getMappedName(index: Int): String {
        return boneNamesMap[boneIndexesMap[index]]!!
    }
}