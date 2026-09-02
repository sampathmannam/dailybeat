package com.dailybeat.app.backup

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class BackupSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMs: Long,
)

interface BackupSessionStore {
    fun get(): BackupSession?
    fun save(session: BackupSession)
    fun clear()
}

class EncryptedBackupSessionStore(context: Context) : BackupSessionStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun get(): BackupSession? = runCatching {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf(String::isNotBlank) ?: return null
        BackupSession(
            userId = userId,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            expiresAtMs = prefs.getLong(KEY_EXPIRES_AT, 0L),
        ).takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
    }.getOrNull()

    override fun save(session: BackupSession) {
        val stored = prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMs)
            // Synchronous commit so the boolean return value can gate the follow-up check.
            // apply() is asynchronous and would let a failed write slip past the assertion below.
            .commit()
        check(stored) { "Unable to store cloud backup session securely." }
    }

    override fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val FILE_NAME = "dailybeat_backup_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
