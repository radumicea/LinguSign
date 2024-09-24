package radu.signlanguageinterpreter.globals

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.io.UpdateManager
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun postDelayed(r: Runnable, delayMillis: Long) {
    Handler.createAsync(Looper.getMainLooper()).postDelayed(r, delayMillis)
}

fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

fun Activity.hideKeyboard() {
    hideKeyboard(currentFocus ?: View(this))
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

fun Activity.showDialogAndExit() {
    runOnUiThread {
        val builder =
            AlertDialog.Builder(this).setTitle(Application.getString(R.string.request_failed))
                .setMessage(Application.getString(R.string.no_internet_dialog_text))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finishAndRemoveTask()
                }

        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }
}

fun Activity.showDialogAndUpdate() {
    runOnUiThread {
        val builder =
            AlertDialog.Builder(this).setTitle(Application.getString(R.string.update_needed))
                .setMessage(Application.getString(R.string.update_needed_text))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    UpdateManager.doUpdate(this)
                }.setNegativeButton(Application.getString(R.string.exit)) { dialog, _ ->
                    dialog.dismiss()
                    finishAndRemoveTask()
                }

        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }
}

fun loadModelFile(path: String): MappedByteBuffer {
    val file = File(path)
    val input = FileInputStream(file)
    return input.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
}

fun NormalizedLandmark.mirror(): NormalizedLandmark {
    return NormalizedLandmark.create(1 - x(), y(), z())
}