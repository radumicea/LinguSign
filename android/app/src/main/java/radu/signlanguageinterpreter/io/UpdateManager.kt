package radu.signlanguageinterpreter.io

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.globals.DOWNLOADS_FLAG
import radu.signlanguageinterpreter.globals.DOWNLOADS_IDS_SET
import radu.signlanguageinterpreter.globals.DOWNLOADS_PREFS_NAME
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.globals.showDialogAndExit
import radu.signlanguageinterpreter.globals.showDialogAndUpdate
import java.io.File

object UpdateManager {
    fun doUpdate(activity: Activity) = CoroutineScope(Dispatchers.IO).launch {
        SharedState.updating.set(true)
        SharedState.updatingObservable.postValue(true)

        val hashFiles = Hasher.hashDirectory(Application.dataDirPath)
        val result = HttpClient.post(
            "update", object : TypeToken<Map<String, List<String>>>() {}, hashFiles
        )

        if (result.isFailure) {
            activity.showDialogAndExit()
            return@launch
        }

        val updates = result.getOrThrow()

        val toBeDeleted = updates["toBeDeleted"]!!
        val toBeAdded = updates["toBeAdded"]!!

        IOUtils.deleteFiles(toBeDeleted.map { fileName ->
            Application.dataDirPath + File.separator + fileName.replace(
                "/", File.separator
            )
        })

        if (toBeAdded.isEmpty()) {
            SharedState.updating.set(false)
            SharedState.updatingObservable.postValue(false)
        } else {
            IOUtils.downloadFiles(Application.dataDirName, toBeAdded)
        }
    }

    fun doUpdateIfNoFilesDownloadedOrIfAlreadyStarted(activity: Activity) {
        val preferences = ConcurrentSharedPreferences[DOWNLOADS_PREFS_NAME]

        val dataDir = File(Application.dataDirPath)
        if (!dataDir.exists()) {
            preferences.setBoolean(DOWNLOADS_FLAG, true)
            dataDir.mkdirs()
            activity.showDialogAndUpdate()
        } else {
            val downloadIds = preferences.getSet(
                DOWNLOADS_IDS_SET
            ).map { it.toLong() }.toLongArray()

            if (downloadIds.isNotEmpty()) {
                val downloadManager =
                    Application.context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(*downloadIds)
                preferences.clearSet(DOWNLOADS_IDS_SET)
                doUpdate(activity)
            } else {
                if (preferences.getBoolean(DOWNLOADS_FLAG)) {
                    doUpdate(activity)
                }
            }
        }
    }
}