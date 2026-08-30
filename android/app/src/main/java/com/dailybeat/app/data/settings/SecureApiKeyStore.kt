package com.dailybeat.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    fun getApiKey(): String? = runCatching {
        prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun setApiKey(key: String) {
        try {
            check(prefs.edit().putString(KEY_API, key.trim()).commit())
        } catch (error: Exception) {
            throw IllegalStateException("Unable to store the API key securely on this device.", error)
        }
    }

    fun clearApiKey() {
        runCatching { prefs.edit().remove(KEY_API).commit() }
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    private companion object {
        private const val KEY_API = "cloud_llm_api_key"
        private const val ENCRYPTED_FILE = "dailybeat_secure"
        private fun createPrefs(context: Context): SharedPreferences {
            return EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
