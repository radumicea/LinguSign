package radu.signlanguageinterpreter.io

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import radu.signlanguageinterpreter.globals.DOWNLOADS_FLAG
import radu.signlanguageinterpreter.globals.DOWNLOADS_IDS_SET
import radu.signlanguageinterpreter.globals.DOWNLOADS_PREFS_NAME
import radu.signlanguageinterpreter.globals.SharedState

class DownloadsReceiver : BroadcastReceiver() {
    private val preferences = ConcurrentSharedPreferences[DOWNLOADS_PREFS_NAME]

    override fun onReceive(context: Context?, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        context?.let {
            val (removed, empty) = preferences.removeFromSetAndCheckRemovedAndEmpty(
                DOWNLOADS_IDS_SET,
                downloadId.toString()
            )
            if (removed) {
                val progress = SharedState.downloadProgress.incrementAndGet()
                SharedState.downloadProgressObservable.postValue(progress)

                if (empty) {
                    preferences.setBoolean(DOWNLOADS_FLAG, false)
                    SharedState.updating.set(false)
                    SharedState.updatingObservable.postValue(false)
                }
            }
        }
    }
}