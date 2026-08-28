package com.dailybeat.app.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureApiKeyStore(private val context: Context) {

    private val prefs: SharedPreferences = createPrefs(context)

    fun getApiKey(): String? = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove(KEY_API).apply()
    }

    fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()

    private companion object {
        private const val KEY_API = "cloud_llm_api_key"
        private const val ENCRYPTED_FILE = "dailybeat_secure"
        private const val FALLBACK_FILE = "dailybeat_secure_fallback"

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_FILE,
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (_: Exception) {
                context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
            }
        }
    }
}
