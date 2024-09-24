package radu.signlanguageinterpreter.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import radu.signlanguageinterpreter.databinding.ActivityAuthenticateBinding
import radu.signlanguageinterpreter.globals.afterTextChanged
import radu.signlanguageinterpreter.globals.hideKeyboard
import radu.signlanguageinterpreter.view_models.AuthenticateViewModel

class AuthenticateActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuthenticateBinding
    private lateinit var viewModel: AuthenticateViewModel

    private var loginButtonEnabled = false
    private var registerButtonEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthenticateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onBackPressedDispatcher.addCallback(this) {}

        viewModel = ViewModelProvider(this)[AuthenticateViewModel::class.java]
        viewModel.init()

        viewModel.loginError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
                setResult(Activity.RESULT_OK)
            }
        }

        viewModel.registerError.observe(this) { errorMessage ->
            if (errorMessage != null) {
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_SHORT).show()
            } else {
                switchToLogin()
            }
        }

        viewModel.isLoading.observe(this) {
            if (it) {
                loginButtonEnabled = binding.loginButton.isEnabled
                registerButtonEnabled = binding.registerButton.isEnabled

                binding.loginButton.isEnabled = false
                binding.registerButton.isEnabled = false
                binding.loading.visibility = View.VISIBLE
            } else {
                binding.loading.visibility = View.GONE
                binding.loginButton.isEnabled = loginButtonEnabled
                binding.registerButton.isEnabled = registerButtonEnabled
            }
        }

        setupLogin()

        setupRegister()
    }

    private fun setupLogin() {
        val userName = binding.userName.editText!!
        val password = binding.password.editText!!
        val loginButton = binding.loginButton

        userName.afterTextChanged {
            viewModel.loginDataChanged(userName, password, loginButton)
        }

        password.apply {
            afterTextChanged {
                viewModel.loginDataChanged(userName, password, loginButton)
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> {
                        handleLogin(userName.text.toString(), password.text.toString())
                    }
                }
                false
            }
        }

        loginButton.setOnClickListener {
            handleLogin(userName.text.toString(), password.text.toString())
        }

        binding.registerTextButton.setOnClickListener {
            switchToRegister()
        }
    }

    private fun setupRegister() {
        val userName = binding.registerUserName.editText!!
        val password = binding.registerPassword.editText!!
        val confirmPassword = binding.registerConfirmPassword.editText!!
        val registerButton = binding.registerButton

        userName.afterTextChanged {
            viewModel.registerDataChanged(userName, password, confirmPassword, registerButton)
        }

        password.afterTextChanged {
            viewModel.registerDataChanged(userName, password, confirmPassword, registerButton)
        }

        confirmPassword.apply {
            afterTextChanged {
                viewModel.registerDataChanged(userName, password, confirmPassword, registerButton)
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE -> {
                        handleRegister(userName.text.toString(), password.text.toString())
                    }
                }
                false
            }
        }

        registerButton.setOnClickListener {
            handleRegister(userName.text.toString(), password.text.toString())
        }

        binding.loginTextButton.setOnClickListener {
            switchToLogin()
        }
    }

    private fun handleLogin(userName: String, password: String) {
        hideKeyboard()
        lifecycleScope.launch(Dispatchers.IO) { viewModel.login(userName, password) }
    }

    private fun handleRegister(userName: String, password: String) {
        hideKeyboard()
        lifecycleScope.launch(Dispatchers.IO) { viewModel.register(userName, password) }
    }

    private fun switchToRegister() {
        binding.loginTextView.visibility = View.GONE
        binding.userName.visibility = View.GONE
        binding.password.visibility = View.GONE
        binding.registerTextButton.visibility = View.GONE
        binding.loginButton.visibility = View.GONE

        binding.registerTextView.visibility = View.VISIBLE
        binding.registerUserName.visibility = View.VISIBLE
        binding.registerPassword.visibility = View.VISIBLE
        binding.registerConfirmPassword.visibility = View.VISIBLE
        binding.loginTextButton.visibility = View.VISIBLE
        binding.registerButton.visibility = View.VISIBLE
    }

    private fun switchToLogin() {
        binding.registerTextView.visibility = View.GONE
        binding.registerUserName.visibility = View.GONE
        binding.registerPassword.visibility = View.GONE
        binding.registerConfirmPassword.visibility = View.GONE
        binding.loginTextButton.visibility = View.GONE
        binding.registerButton.visibility = View.GONE

        binding.loginTextView.visibility = View.VISIBLE
        binding.userName.visibility = View.VISIBLE
        binding.password.visibility = View.VISIBLE
        binding.registerTextButton.visibility = View.VISIBLE
        binding.loginButton.visibility = View.VISIBLE

    }
}