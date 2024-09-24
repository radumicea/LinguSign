package radu.signlanguageinterpreter.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.os.Bundle
import android.view.OrientationEventListener
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.databinding.ActivityCameraBinding
import radu.signlanguageinterpreter.fragments.CameraFragment
import radu.signlanguageinterpreter.fragments.PermissionFragment

class CameraActivity : AppCompatActivity() {
    private lateinit var orientationEventListener: OrientationEventListener
    private lateinit var binding: ActivityCameraBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setOrientation(resources.configuration.orientation)

        orientationEventListener =
            object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    setOrientation(orientation)
                }
            }
        orientationEventListener.enable()

        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction().replace(
            R.id.fragment_container, CameraFragment()
        ).commit()

        onBackPressedDispatcher.addCallback(this) {
            startActivity(
                Intent(
                    this@CameraActivity, HomeActivity::class.java
                )
            )
            finish()
            setResult(RESULT_OK)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, PermissionFragment()).commit()
        }
    }

    override fun onDestroy() {
        orientationEventListener.disable()
        super.onDestroy()
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setOrientation(orientation: Int) {
        if (orientation in 0..135 || orientation in 316..359) {
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        } else if (orientation in 136..315) {
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        } else {
            if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }
}