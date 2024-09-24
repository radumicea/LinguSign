package radu.signlanguageinterpreter.io

import android.content.Context
import android.content.SharedPreferences
import radu.signlanguageinterpreter.Application
import java.util.concurrent.locks.ReentrantLock

class ConcurrentSharedPreferences private constructor(private val preferences: SharedPreferences) {
    companion object {
        private val preferencesMap = mutableMapOf<String, ConcurrentSharedPreferences>()

        operator fun get(name: String): ConcurrentSharedPreferences {
            var instance = preferencesMap[name]
            if (instance == null) {
                synchronized(this) {
                    instance = preferencesMap[name]
                    if (instance == null) {
                        val preferences =
                            Application.context.getSharedPreferences(name, Context.MODE_PRIVATE)
                        instance = ConcurrentSharedPreferences(preferences)
                        preferencesMap[name] = instance!!
                    }
                }
            }
            return instance!!
        }
    }

    private val lock = ReentrantLock(true)

    fun getBoolean(key: String): Boolean {
        lock.lock()
        try {
            return preferences.getBoolean(key, false)
        } finally {
            lock.unlock()
        }
    }

    fun setBoolean(key: String, value: Boolean) {
        lock.lock()
        try {
            val editor = preferences.edit()
            editor.putBoolean(key, value)
            editor.apply()
        } finally {
            lock.unlock()
        }
    }

    fun addToSet(key: String, value: String) {
        lock.lock()
        try {
            val editor = preferences.edit()
            val set = preferences.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            set.add(value)
            editor.putStringSet(key, set)
            editor.apply()
        } finally {
            lock.unlock()
        }
    }

    fun removeFromSetAndCheckRemovedAndEmpty(key: String, value: String): Pair<Boolean, Boolean> {
        lock.lock()
        try {
            val editor = preferences.edit()
            val set = preferences.getStringSet(key, null)?.toMutableSet()

            if (set?.remove(value) != true) {
                return Pair(false, set?.isEmpty() ?: true)
            }

            editor.putStringSet(key, set)
            editor.apply()
            return Pair(true, set.isEmpty())
        } finally {
            lock.unlock()
        }
    }

    fun getSet(key: String): Set<String> {
        lock.lock()
        try {
            return preferences.getStringSet(key, null)?.toSet() ?: emptySet()
        } finally {
            lock.unlock()
        }
    }

    fun clearSet(key: String) {
        lock.lock()
        try {
            val editor = preferences.edit()
            editor.remove(key)
            editor.apply()
        } finally {
            lock.unlock()
        }
    }
}