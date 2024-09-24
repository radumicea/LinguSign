package radu.signlanguageinterpreter.io

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object Hasher {
    fun md5File(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        var fileInputStream: FileInputStream? = null
        try {
            fileInputStream = FileInputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        } finally {
            fileInputStream?.close()
        }

        val md5Bytes = digest.digest()

        return Base64.encodeToString(md5Bytes, Base64.NO_WRAP)
    }

    suspend fun hashDirectory(dirPath: String): List<Map<String, String>> =
        withContext(Dispatchers.IO) {
            val directory = File(dirPath)
            if (!directory.isDirectory) {
                throw IllegalArgumentException("Not a directory")
            }
            hashDirectoryRecursive(directory, "")
        }

    private suspend fun hashDirectoryRecursive(
        directory: File,
        relativePath: String
    ): List<Map<String, String>> = coroutineScope {
        val deferredResults = directory.listFiles()?.map { file ->
            async {
                if (file.isDirectory) {
                    hashDirectoryRecursive(file, "$relativePath${file.name}/")
                } else {
                    listOf(
                        mapOf(
                            "name" to "$relativePath${file.name}",
                            "hash" to md5File(file)
                        )
                    )
                }
            }
        } ?: emptyList()

        deferredResults.awaitAll().flatten()
    }
}