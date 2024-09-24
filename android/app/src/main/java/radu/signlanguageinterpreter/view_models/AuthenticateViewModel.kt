package radu.signlanguageinterpreter.view_models

import android.widget.Button
import android.widget.EditText
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import radu.signlanguageinterpreter.Application
import radu.signlanguageinterpreter.R
import radu.signlanguageinterpreter.io.HttpClient

class AuthenticateViewModel : ViewModel() {
    private lateinit var _loginError: MutableLiveData<String?>
    val loginError: LiveData<String?>
        get() = _loginError

    private lateinit var _registerError: MutableLiveData<String?>
    val registerError: LiveData<String?>
        get() = _registerError

    private lateinit var _isLoading: MutableLiveData<Boolean>
    val isLoading: LiveData<Boolean>
        get() = _isLoading

    fun init() {
        _loginError = MutableLiveData()
        _registerError = MutableLiveData()
        _isLoading = MutableLiveData()
    }

    suspend fun login(userName: String, password: String) {
        _isLoading.postValue(true)

        val result = HttpClient.login(userName, password)
        if (result.isSuccess) {
            _loginError.postValue(null)
        } else {
            val error = result.exceptionOrNull()!! as HttpClient.HttpClientException
            val errorMessage = if (error.code == 401 || error.code == 404) {
                Application.getString(R.string.wrong_credentials)
            } else {
                Application.getString(R.string.unknown_error)
            }
            _loginError.postValue(errorMessage)
        }

        _isLoading.postValue(false)
    }

    suspend fun register(userName: String, password: String) {
        _isLoading.postValue(true)

        val result = HttpClient.register(userName, password)
        if (result.isSuccess) {
            _registerError.postValue(null)
        } else {
            val error = result.exceptionOrNull()!! as HttpClient.HttpClientException
            val errorMessage = if (error.code == 409) {
                Application.getString(R.string.user_exists)
            } else {
                Application.getString(R.string.unknown_error)
            }
            _registerError.postValue(errorMessage)
        }

        _isLoading.postValue(false)
    }

    fun loginDataChanged(userName: EditText, password: EditText, button: Button) {
        button.isEnabled = true

        if (!isUserNameValid(userName.text.toString())) {
            userName.error = Application.getString(R.string.invalid_user_name)
            button.isEnabled = false
        }
        if (!isPasswordValid(password.text.toString())) {
            password.error = Application.getString(R.string.invalid_password)
            button.isEnabled = false
        }
    }

    fun registerDataChanged(
        userName: EditText,
        password: EditText,
        confirmPassword: EditText,
        button: Button
    ) {
        button.isEnabled = true

        if (!isUserNameValid(userName.text.toString())) {
            userName.error = Application.getString(R.string.invalid_user_name)
            button.isEnabled = false
        }
        if (!isPasswordValid(password.text.toString())) {
            password.error = Application.getString(R.string.invalid_password)
            button.isEnabled = false
        }
        if (!isPasswordValid(confirmPassword.text.toString())) {
            confirmPassword.error = Application.getString(R.string.invalid_password)
            button.isEnabled = false
        }
        if (confirmPassword.text.toString() != password.text.toString()) {
            confirmPassword.error = Application.getString(R.string.passwords_no_match)
            button.isEnabled = false
        }
    }

    private fun isUserNameValid(userName: String): Boolean {
        val r = Regex("""^[a-zA-Z0-9\-._@+]{2,50}$""")
        return r matches userName
    }

    private fun isPasswordValid(password: String): Boolean {
        val r =
            Regex("""^(?=.*[0-9])(?=.*[a-z])(?=.*['"\\`*!@$%^#&(){}\[\]:;<>,.?/~_+\-=|]).{6,}$""")
        return r matches password
    }
}