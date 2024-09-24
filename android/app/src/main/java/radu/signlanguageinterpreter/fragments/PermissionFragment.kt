package radu.signlanguageinterpreter.fragments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.activities.AnimationActivity
import radu.signlanguageinterpreter.activities.CameraActivity
import radu.signlanguageinterpreter.activities.HomeActivity
import radu.signlanguageinterpreter.activities.MirrorActivity
import java.util.concurrent.atomic.AtomicBoolean

class PermissionFragment : Fragment() {
    companion object {
        val dialogOpen = AtomicBoolean(false)
    }

    private lateinit var activity: Activity

    private fun goToSettings(stringCode: Int) {
        dialogOpen.set(true)

        // Show dialog
        val builder =
            AlertDialog.Builder(activity).setTitle(Application.getString(R.string.need_permissions))
                .setMessage(Application.getString(stringCode))
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()

                    // Go home
                    startActivity(Intent(activity, HomeActivity::class.java))
                    activity.finish()
                    activity.setResult(Activity.RESULT_OK)

                    // Then go to settings
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", requireContext().packageName, null)
                    intent.setData(uri)
                    startActivity(intent)

                    dialogOpen.set(false)
                }

        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.show()
    }

    private val requestRecordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            parentFragmentManager.popBackStack()
        } else {
            goToSettings(R.string.need_record_audio_permissions)
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            parentFragmentManager.popBackStack()
        } else {
            goToSettings(R.string.need_camera_permissions)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!dialogOpen.get()) {
            activity = requireActivity()
            if (activity is AnimationActivity) {
                if (ContextCompat.checkSelfPermission(
                        activity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    parentFragmentManager.popBackStack()
                } else {
                    requestRecordAudioPermissionLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )
                }
            } else if (activity is CameraActivity || activity is MirrorActivity) {
                if (ContextCompat.checkSelfPermission(
                        activity, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    parentFragmentManager.popBackStack()
                } else {
                    requestCameraPermissionLauncher.launch(
                        Manifest.permission.CAMERA
                    )
                }
            } else {
                throw IllegalStateException()
            }
        }
    }
}