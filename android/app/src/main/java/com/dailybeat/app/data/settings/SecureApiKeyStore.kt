package com.dailybeat.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dailybeat.app.cloud.ApiKeySource

open class SecureApiKeyStore(private val context: Context) : ApiKeySource {

    private val prefs: SharedPreferences by lazy { createPrefs(context) }

    override fun getApiKey(): String? = runCatching {
        prefs.getString(KEY_API, null)?.takeIf {
            it.isNotBlank() && it.length <= MAX_API_KEY_CHARS
        }
    }.getOrNull()

    open fun setApiKey(key: String) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API key cannot be empty." }
        require(trimmed.length <= MAX_API_KEY_CHARS) { "API key is too long." }
        try {
            check(prefs.edit().putString(KEY_API, trimmed).commit())
        } catch (error: Exception) {
            throw IllegalStateException("Unable to store the API key securely on this device.", error)
        }
    }

    open fun clearApiKey() {
        try {
            check(prefs.edit().remove(KEY_API).commit())
        } catch (error: Exception) {
            throw IllegalStateException("Unable to remove the API key securely from this device.", error)
        }
    }

    open fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    private companion object {
        private const val KEY_API = "cloud_llm_api_key"
        private const val ENCRYPTED_FILE = "dailybeat_secure"
        private const val MAX_API_KEY_CHARS = 4_096
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
