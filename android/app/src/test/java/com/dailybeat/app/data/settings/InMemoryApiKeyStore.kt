package com.dailybeat.app.data.settings

import android.content.Context

/**
 * Stands in for [SecureApiKeyStore] in JVM tests. The production store is backed by
 * EncryptedSharedPreferences, and the AndroidKeyStore it needs is unavailable off-device.
 */
class InMemoryApiKeyStore(context: Context, private var key: String? = "test-api-key") :
    SecureApiKeyStore(context) {
    override fun getApiKey(): String? = key?.takeIf { it.isNotBlank() }
    override fun setApiKey(key: String) { this.key = key.trim() }
    override fun clearApiKey() { key = null }
    override fun hasApiKey(): Boolean = !getApiKey().isNullOrBlank()
}
