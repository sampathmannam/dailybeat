package com.dailybeat.app.backup

import android.content.Context
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

class EncryptedBackupSessionStore(private val context: Context) : BackupSessionStore {
    // Opening encrypted preferences can fail after a device restore or keystore invalidation.
    // Keep that failure inside get/save instead of crashing every Settings screen construction.
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun get(): BackupSession? = runCatching {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf(String::isNotBlank) ?: return null
        BackupSession(
            userId = userId,
            email = prefs.getString(KEY_EMAIL, "").orEmpty(),
            accessToken = prefs.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            expiresAtMs = prefs.getLong(KEY_EXPIRES_AT, 0L),
        ).takeIf {
            it.userId.length <= MAX_USER_ID_CHARS &&
                it.email.length <= MAX_EMAIL_CHARS &&
                it.accessToken.isNotBlank() && it.accessToken.length <= MAX_TOKEN_CHARS &&
                it.refreshToken.isNotBlank() && it.refreshToken.length <= MAX_TOKEN_CHARS
        }
    }.getOrNull()

    override fun save(session: BackupSession) {
        require(session.userId.isNotBlank() && session.userId.length <= MAX_USER_ID_CHARS) {
            "Cloud backup returned an invalid user ID."
        }
        require(session.email.length <= MAX_EMAIL_CHARS) { "Cloud backup returned an invalid email." }
        require(
            session.accessToken.isNotBlank() && session.accessToken.length <= MAX_TOKEN_CHARS &&
                session.refreshToken.isNotBlank() && session.refreshToken.length <= MAX_TOKEN_CHARS,
        ) { "Cloud backup returned an invalid session." }
        try {
            val stored = prefs.edit()
                .putString(KEY_USER_ID, session.userId)
                .putString(KEY_EMAIL, session.email)
                .putString(KEY_ACCESS_TOKEN, session.accessToken)
                .putString(KEY_REFRESH_TOKEN, session.refreshToken)
                .putLong(KEY_EXPIRES_AT, session.expiresAtMs)
                .commit()
            check(stored) { "Unable to store cloud backup session securely." }
        } catch (error: Exception) {
            throw IllegalStateException("Unable to store cloud backup session securely.", error)
        }
    }

    override fun clear() {
        runCatching { prefs.edit().clear().commit() }
            .onFailure { context.deleteSharedPreferences(FILE_NAME) }
    }

    private companion object {
        const val FILE_NAME = "dailybeat_backup_session"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at"
        const val MAX_USER_ID_CHARS = 256
        const val MAX_EMAIL_CHARS = 320
        const val MAX_TOKEN_CHARS = 131_072
    }
}
