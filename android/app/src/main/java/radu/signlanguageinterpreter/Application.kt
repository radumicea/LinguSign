package radu.signlanguageinterpreter

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.filament.utils.Utils
import java.io.File

class Application : Application() {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var _context: Context? = null
        val context: Context
            get() = _context!!

        private var _dataDirPath: String? = null
        val dataDirPath: String
            get() = _dataDirPath!!

        const val dataDirName = "SignLanguageInterpreter"

        fun getEncryptedSharedPreferences(name: String): SharedPreferences {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        fun getString(resId: Int): String {
            return context.getString(resId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        _context = applicationContext

        _dataDirPath =
            context.getExternalFilesDir(null)!!.absolutePath + File.separator + dataDirName

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context))
        }

        Utils.init()
    }
}