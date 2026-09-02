package com.dailybeat.app.backup

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class RemoteBackup(
    val snapshotJson: String,
    val updatedAt: String,
)

data class BackupSignUpResult(
    val session: BackupSession?,
    val requiresEmailConfirmation: Boolean,
)

interface BackupRemote {
    val isConfigured: Boolean
    fun currentSession(): BackupSession?
    suspend fun authenticatedSession(): Result<BackupSession>
    suspend fun signUp(email: String, password: String): Result<BackupSignUpResult>
    suspend fun signIn(email: String, password: String): Result<BackupSession>
    suspend fun upload(snapshotJson: String): Result<Unit>
    suspend fun download(): Result<RemoteBackup?>
    suspend fun revokeSession(): Result<Unit> {
        signOut()
        return Result.success(Unit)
    }
    fun signOut()
}

open class BackupRemoteException(message: String) : IllegalStateException(message)

class BackupSessionExpiredException :
    BackupRemoteException("Cloud authorization expired. Sign in again.")

class BackupTransientException(message: String) : BackupRemoteException(message)

fun Throwable.isTransientBackupFailure(): Boolean =
    this is IOException || this is BackupTransientException

class SupabaseBackupClient(
    private val configuration: BackupConfiguration,
    private val sessionStore: BackupSessionStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) : BackupRemote {
    override val isConfigured: Boolean get() = configuration.isConfigured

    override fun currentSession(): BackupSession? = sessionStore.get()

    override suspend fun authenticatedSession(): Result<BackupSession> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            validSession()
        }
    }

    override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureConfigured()
                require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required." }
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                    .toString()
                val request = requestBuilder("/auth/v1/signup")
                    .post(body.toRequestBody(JSON))
                    .build()
                val responseBody = execute(request, authRequest = true)
                val root = JSONObject(responseBody)
                if (root.optString("access_token").isNotBlank()) {
                    val session = parseSession(responseBody).also(sessionStore::save)
                    BackupSignUpResult(session = session, requiresEmailConfirmation = false)
                } else {
                    BackupSignUpResult(session = null, requiresEmailConfirmation = true)
                }
            }
        }

    override suspend fun signIn(email: String, password: String): Result<BackupSession> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required." }
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
            val request = requestBuilder("/auth/v1/token?grant_type=password")
                .post(body.toRequestBody(JSON))
                .build()
            val responseBody = execute(request, authRequest = true)
            parseSession(responseBody).also(sessionStore::save)
        }
    }

    override suspend fun upload(snapshotJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val session = validSession()
            val payload = JSONObject()
                .put("user_id", session.userId)
                .put("snapshot", JSONObject(snapshotJson))
                .toString()
            val request = authorizedRequestBuilder(
                "/rest/v1/dailybeat_backups?on_conflict=user_id",
                session,
            )
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toRequestBody(JSON))
                .build()
            execute(request)
            Unit
        }
    }

    override suspend fun download(): Result<RemoteBackup?> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val session = validSession()
            val request = authorizedRequestBuilder(
                "/rest/v1/dailybeat_backups?select=snapshot,updated_at&user_id=eq.${session.userId}&limit=1",
                session,
            ).get().build()
            val rows = JSONArray(execute(request))
            if (rows.length() == 0) {
                null
            } else {
                val row = rows.getJSONObject(0)
                RemoteBackup(
                    snapshotJson = row.getJSONObject("snapshot").toString(),
                    updatedAt = row.getString("updated_at"),
                )
            }
        }
    }

    override fun signOut() {
        sessionStore.clear()
    }

    override suspend fun revokeSession(): Result<Unit> = withContext(Dispatchers.IO) {
        val session = sessionStore.get()
        sessionStore.clear()
        if (session == null) return@withContext Result.success(Unit)
        runCatching {
            ensureConfigured()
            val request = authorizedRequestBuilder("/auth/v1/logout?scope=local", session)
                .post("{}".toRequestBody(JSON))
                .build()
            try {
                execute(request)
            } catch (_: BackupSessionExpiredException) {
                // The server already considers this session unusable.
            }
            Unit
        }
    }

    private suspend fun validSession(): BackupSession {
        val current = sessionStore.get()
            ?: throw IllegalStateException("Sign in to use cloud backup.")
        if (current.expiresAtMs > clock() + REFRESH_EARLY_MS) return current

        val body = JSONObject()
            .put("refresh_token", current.refreshToken)
            .toString()
        val request = requestBuilder("/auth/v1/token?grant_type=refresh_token")
            .post(body.toRequestBody(JSON))
            .build()
        return try {
            parseSession(execute(request, sessionRefresh = true), current).also(sessionStore::save)
        } catch (error: Exception) {
            if (error is BackupSessionExpiredException) sessionStore.clear()
            throw error
        }
    }

    private fun parseSession(body: String, fallback: BackupSession? = null): BackupSession {
        val root = JSONObject(body)
        val user = root.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf(String::isNotBlank) ?: fallback?.userId
        val email = user?.optString("email")?.takeIf(String::isNotBlank) ?: fallback?.email.orEmpty()
        return BackupSession(
            userId = requireNotNull(userId) { "Cloud backup sign-in returned an invalid session." },
            email = email,
            accessToken = root.getString("access_token"),
            refreshToken = root.optString("refresh_token").takeIf(String::isNotBlank)
                ?: fallback?.refreshToken
                ?: throw IllegalStateException("Cloud backup sign-in returned an invalid session."),
            expiresAtMs = clock() + root.optLong("expires_in", 3600L) * 1_000L,
        )
    }

    private fun requestBuilder(path: String): Request.Builder = Request.Builder()
        .url(configuration.baseUrl + path)
        .header("apikey", configuration.anonymousKey)
        .header("Content-Type", "application/json")

    private fun authorizedRequestBuilder(path: String, session: BackupSession): Request.Builder =
        requestBuilder(path).header("Authorization", "Bearer ${session.accessToken}")

    private fun execute(
        request: Request,
        authRequest: Boolean = false,
        sessionRefresh: Boolean = false,
    ): String {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val error = when {
                    authRequest && response.code in 400..499 ->
                        BackupRemoteException("Email or password is incorrect.")
                    sessionRefresh && response.code in 400..499 -> BackupSessionExpiredException()
                    response.code == 401 || response.code == 403 -> BackupSessionExpiredException()
                    response.code == 404 -> BackupRemoteException("No cloud backup was found.")
                    response.code == 429 ->
                        BackupTransientException("Cloud backup is temporarily busy. Try again shortly.")
                    response.code >= 500 ->
                        BackupTransientException("Cloud backup service is temporarily unavailable.")
                    else -> BackupRemoteException("Cloud backup request failed (${response.code}).")
                }
                throw error
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun ensureConfigured() {
        check(configuration.isConfigured) { "Cloud backup is not configured in this build." }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val REFRESH_EARLY_MS = 60_000L
    }
}
