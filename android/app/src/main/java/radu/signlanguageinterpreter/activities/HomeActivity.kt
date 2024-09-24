package radu.signlanguageinterpreter.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.databinding.ActivityHomeBinding
import radu.signlanguageinterpreter.fragments.HomeFragment
import radu.signlanguageinterpreter.fragments.UpdatingFragment
import radu.signlanguageinterpreter.globals.SharedState
import radu.signlanguageinterpreter.globals.TOKEN
import radu.signlanguageinterpreter.globals.TOKENS_PREFS_NAME
import radu.signlanguageinterpreter.io.HttpClient
import radu.signlanguageinterpreter.io.UpdateManager

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val preferences = Application.getEncryptedSharedPreferences(TOKENS_PREFS_NAME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, HomeFragment()).commit()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this) {}

        SharedState.updatingObservable.observe(this) {
            if (it) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, UpdatingFragment()).commit()
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (!checkLoggedIn()) {
                return@launch
            }
            UpdateManager.doUpdateIfNoFilesDownloadedOrIfAlreadyStarted(this@HomeActivity)
        }
    }

    private suspend fun checkLoggedIn(): Boolean {
        val token = preferences.getString(TOKEN, null)

        if (token == null) {
            startActivity(Intent(this, AuthenticateActivity::class.java))
            finish()
            setResult(Activity.RESULT_OK)
            return false
        }

        if (HttpClient.isJwtExpired(token)) {
            val result = HttpClient.refreshToken(token)
            if (result.isFailure) {
                startActivity(Intent(this, AuthenticateActivity::class.java))
                finish()
                setResult(Activity.RESULT_OK)
                return false
            }
        }

        return true
    }
}