package radu.signlanguageinterpreter.io

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.BuildConfig
import radu.signlanguageinterpreter.globals.DOWNLOADS_FLAG
import radu.signlanguageinterpreter.globals.DOWNLOADS_IDS_SET
import radu.signlanguageinterpreter.globals.DOWNLOADS_PREFS_NAME
import radu.signlanguageinterpreter.globals.SharedState
import java.io.File

object IOUtils {
    private val preferences = ConcurrentSharedPreferences[DOWNLOADS_PREFS_NAME]

    suspend fun deleteFiles(filePaths: List<String>) = withContext(Dispatchers.IO) {
        filePaths.map { path ->
            async {
                File(path).delete()
            }
        }.awaitAll()
    }

    suspend fun downloadFiles(dirName: String, fileNames: List<String>) =
        withContext(Dispatchers.IO) {
            preferences.setBoolean(DOWNLOADS_FLAG, true)

            SharedState.totalDownloadCount.set(fileNames.size)
            SharedState.downloadProgress.set(0)
            SharedState.downloadProgressObservable.postValue(0)

            val downloadManager =
                Application.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

            fileNames.forEach { fileName ->
                val split = fileName.split("/")
                val request =
                    DownloadManager
                        .Request(Uri.parse(BuildConfig.API_URI + "file/" + split[0] + "/" + split[1]))
                        .setTitle(fileName)
                        .setDestinationInExternalFilesDir(
                            Application.context,
                            null,
                            dirName + File.separator + split[0] + File.separator + split[1]
                        )

                preferences.addToSet(
                    DOWNLOADS_IDS_SET,
                    downloadManager.enqueue(request).toString()
                )
            }
        }
}