package radu.signlanguageinterpreter.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.databinding.ActivityAnimationBinding
import radu.signlanguageinterpreter.fragments.AnimationFragment
import radu.signlanguageinterpreter.fragments.PermissionFragment

class AnimationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAnimationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAnimationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AnimationFragment()).commit()

        onBackPressedDispatcher.addCallback(this) {
            startActivity(
                Intent(
                    this@AnimationActivity, HomeActivity::class.java
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
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, PermissionFragment()).commit()
        }
    }
}