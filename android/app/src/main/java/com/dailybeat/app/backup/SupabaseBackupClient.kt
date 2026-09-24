package com.dailybeat.app.backup

import com.dailybeat.app.util.isBoundedJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A bare OkHttpClient has callTimeout 0 — unbounded — so a stalled connection could pin a backup
 * coroutine forever behind the retry loop. Redirects are refused outright: these are fixed Supabase
 * endpoints with no reason to redirect, and OkHttp only strips `Authorization` across hosts, not the
 * custom `apikey` header this client sends.
 */
private fun defaultBackupHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .callTimeout(120, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

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
    suspend fun signUp(email: String, password: String): Result<BackupSignUpResult>
    suspend fun signIn(email: String, password: String): Result<BackupSession>
    suspend fun upload(snapshotJson: String): Result<Unit>
    suspend fun download(): Result<RemoteBackup?>
    suspend fun downloadLegacy(): Result<RemoteBackup?> = Result.failure(IllegalStateException("Legacy recovery unavailable."))
    suspend fun deleteCloudData(): Result<Unit> = Result.failure(IllegalStateException("Cloud deletion unavailable."))
    suspend fun deleteAccount(): Result<Unit> = Result.failure(IllegalStateException("Account deletion unavailable."))
    fun signOut()
}

private class BackupHttpException(val status: Int, message: String) : IllegalStateException(message)

class SupabaseBackupClient(
    private val configuration: BackupConfiguration,
    private val sessionStore: BackupSessionStore,
    httpClient: OkHttpClient = defaultBackupHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val networkRetryDelaysMs: List<Long> = listOf(250L, 750L, 1_500L),
    private val authenticationDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ArchiveBackupRemote {
    // Keep credentials on the configured origin even if a caller supplies a shared client.
    private val httpClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val sessionLock = Any()
    private val refreshMutex = Mutex()
    private var sessionGeneration = 0L

    override val isConfigured: Boolean get() = configuration.isConfigured

    override fun currentSession(): BackupSession? = synchronized(sessionLock) { sessionStore.get() }

    override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> {
        // Reserve the request before dispatch. Otherwise a queued old sign-in can start its
        // generation after Sign out/Erase and silently become a new authorized request.
        currentCoroutineContext().ensureActive()
        val generation = beginAuthentication()
        return withContext(authenticationDispatcher) {
            runCatching {
                ensureAuthenticationCurrent(generation)
                ensureConfigured()
                validateCredentials(email, password)
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                    .toString()
                val request = requestBuilder("/auth/v1/signup")
                    .post(body.toRequestBody(JSON))
                    .build()
                // Account creation is not idempotent: a lost success response must not result in
                // an automatic second sign-up request.
                val responseBody = execute(
                    request,
                    authRequest = true,
                    retryNetworkFailures = false,
                )
                val root = responseObject(responseBody)
                if (root.optString("access_token").isNotBlank()) {
                    val session = saveAuthentication(parseSession(responseBody), generation)
                    BackupSignUpResult(session = session, requiresEmailConfirmation = false)
                } else {
                    BackupSignUpResult(session = null, requiresEmailConfirmation = true)
                }
            }
        }
    }

    override suspend fun signIn(email: String, password: String): Result<BackupSession> {
        currentCoroutineContext().ensureActive()
        val generation = beginAuthentication()
        return withContext(authenticationDispatcher) {
            runCatching {
                ensureAuthenticationCurrent(generation)
                ensureConfigured()
                validateCredentials(email, password)
                val body = JSONObject()
                    .put("email", email.trim())
                    .put("password", password)
                    .toString()
                val request = requestBuilder("/auth/v1/token?grant_type=password")
                    .post(body.toRequestBody(JSON))
                    .build()
                val responseBody = execute(request, authRequest = true)
                saveAuthentication(parseSession(responseBody), generation)
            }
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
                "/rest/v1/dailybeat_encrypted_backups?on_conflict=user_id",
                session,
            )
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toRequestBody(JSON))
                .build()
            execute(request)
            Unit
        }
    }

    override suspend fun download(): Result<RemoteBackup?> = downloadFrom("dailybeat_encrypted_backups")
    override suspend fun downloadLegacy(): Result<RemoteBackup?> = downloadFrom("dailybeat_backups")

    override suspend fun deleteCloudData(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val session = validSession()
            listOf("dailybeat_backup_versions", "dailybeat_encrypted_backups", "dailybeat_backups").forEach { table ->
                val request = authorizedRequestBuilder(
                    "/rest/v1/$table?user_id=eq.${session.userId}",
                    session,
                )
                    .header("Prefer", "return=minimal")
                    .delete()
                    .build()
                execute(request)
            }
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val session = validSession()
            val request = authorizedRequestBuilder("/functions/v1/delete-account", session)
                .post("{}".toRequestBody(JSON))
                .build()
            execute(request)
            synchronized(sessionLock) {
                // A different account may have signed in while deletion was in flight.
                if (sessionStore.get()?.userId == session.userId) {
                    sessionGeneration++
                    sessionStore.clear()
                }
            }
        }
    }

    private suspend fun downloadFrom(table: String): Result<RemoteBackup?> = withContext(Dispatchers.IO) {
        runCatching {
            ensureConfigured()
            val session = validSession()
            val request = authorizedRequestBuilder(
                "/rest/v1/$table?select=snapshot,updated_at&user_id=eq.${session.userId}&limit=1",
                session,
            ).get().build()
            val rows = responseArray(execute(request))
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

    override suspend fun abandonVersion(id: String) = withContext(Dispatchers.IO) {
        execute(authorizedRequestBuilder("/rest/v1/dailybeat_backup_versions?id=eq.${checkedId(id)}&manifest=is.null", validSession()).delete().build())
        Unit
    }
    override suspend fun beginVersion(id: String) = archiveRequest("/rest/v1/rpc/begin_dailybeat_backup", JSONObject().put("backup_id", checkedId(id))).let { Unit }
    override suspend fun uploadPart(id: String, index: Int, payload: String) {
        require(index in 0 until ArchiveCipher.MAX_PARTS && payload.length <= ArchiveCipher.MAX_PART_BYTES)
        archiveRequest("/rest/v1/rpc/put_dailybeat_backup_part", JSONObject().put("backup_id", checkedId(id))
            .put("part_index", index).put("part_payload", JSONObject(payload)))
    }
    override suspend fun publishVersion(id: String, manifest: String, parts: Int) {
        archiveRequest("/rest/v1/rpc/publish_dailybeat_backup", JSONObject().put("backup_id", checkedId(id))
            .put("backup_manifest", JSONObject(manifest)).put("part_count", parts))
    }
    override suspend fun versions(): List<BackupVersion> = withContext(Dispatchers.IO) {
        ensureConfigured()
        val rows = responseArray(execute(authorizedRequestBuilder(
            "/rest/v1/dailybeat_backup_versions?select=id,created_at,manifest&manifest=not.is.null&order=created_at.desc&limit=5", validSession()).get().build()))
        (0 until rows.length()).map { index -> rows.getJSONObject(index).let {
            BackupVersion(checkedId(it.getString("id")), it.getString("created_at"), it.getJSONObject("manifest").toString())
        } }
    }
    override suspend fun downloadPart(id: String, index: Int): String = withContext(Dispatchers.IO) {
        require(index in 0 until ArchiveCipher.MAX_PARTS)
        val rows = responseArray(execute(authorizedRequestBuilder(
            "/rest/v1/dailybeat_backup_parts?select=payload&version_id=eq.${checkedId(id)}&part_index=eq.$index&limit=1", validSession()).get().build()))
        check(rows.length() == 1) { "A backup page is missing. Nothing was restored." }
        rows.getJSONObject(0).getJSONObject("payload").toString()
    }
    private suspend fun archiveRequest(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        ensureConfigured()
        execute(authorizedRequestBuilder(path, validSession()).post(body.toString().toRequestBody(JSON)).build())
    }
    private fun checkedId(id: String): String = java.util.UUID.fromString(id).toString().also { require(it == id) }

    override fun signOut() {
        synchronized(sessionLock) {
            sessionGeneration++
            sessionStore.clear()
        }
    }

    private fun beginAuthentication(): Long = synchronized(sessionLock) { ++sessionGeneration }

    private fun ensureAuthenticationCurrent(generation: Long) = synchronized(sessionLock) {
        check(sessionGeneration == generation) { "Cloud backup sign-in changed. Try again." }
    }

    private fun saveAuthentication(session: BackupSession, generation: Long): BackupSession =
        synchronized(sessionLock) {
            check(sessionGeneration == generation) { "Cloud backup sign-in changed. Try again." }
            sessionStore.save(session)
            session
        }

    private suspend fun validSession(): BackupSession = refreshMutex.withLock {
        // Only one caller may rotate a refresh token. Re-read after acquiring the mutex,
        // so other downloads/uploads use the session that the first caller refreshed.
        val (current, generation) = synchronized(sessionLock) {
            (sessionStore.get() ?: throw IllegalStateException("Sign in to use cloud backup.")) to sessionGeneration
        }
        if (current.expiresAtMs > clock() + REFRESH_EARLY_MS) return@withLock current

        val body = JSONObject()
            .put("refresh_token", current.refreshToken)
            .toString()
        val request = requestBuilder("/auth/v1/token?grant_type=refresh_token")
            .post(body.toRequestBody(JSON))
            .build()
        try {
            val refreshed = parseSession(execute(request), current)
            check(refreshed.userId == current.userId) { "Cloud backup returned a different account. Sign in again." }
            synchronized(sessionLock) {
                // Sign out (or a newer sign-in) wins over an older response. Otherwise a
                // late refresh would silently sign the device back in after sign out.
                check(sessionGeneration == generation && sessionStore.get() == current) {
                    "Cloud backup sign-in changed. Try again."
                }
                sessionStore.save(refreshed)
            }
            refreshed
        } catch (error: Exception) {
            if (error is BackupHttpException && error.status in setOf(400, 401, 403)) {
                synchronized(sessionLock) {
                    if (sessionGeneration == generation && sessionStore.get() == current) {
                        sessionGeneration++
                        sessionStore.clear()
                    }
                }
            }
            throw error
        }
    }

    private fun parseSession(body: String, fallback: BackupSession? = null): BackupSession {
        val root = responseObject(body)
        val user = root.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf(String::isNotBlank) ?: fallback?.userId
        val email = user?.optString("email")?.takeIf(String::isNotBlank) ?: fallback?.email.orEmpty()
        val accessToken = root.getString("access_token")
        val refreshToken = root.optString("refresh_token").takeIf(String::isNotBlank)
            ?: fallback?.refreshToken
            ?: throw IllegalStateException("Cloud backup sign-in returned an invalid session.")
        check(accessToken.length <= MAX_TOKEN_CHARS && refreshToken.length <= MAX_TOKEN_CHARS) {
            "Cloud backup sign-in returned an invalid session."
        }
        val expiresInSeconds = root.optLong("expires_in", 3600L).coerceIn(60L, MAX_SESSION_SECONDS)
        return BackupSession(
            userId = requireNotNull(userId) { "Cloud backup sign-in returned an invalid session." },
            email = email,
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtMs = clock() + expiresInSeconds * 1_000L,
        )
    }

    private fun requestBuilder(path: String): Request.Builder = Request.Builder()
        .url(configuration.baseUrl + path)
        .header("apikey", configuration.anonymousKey)
        .header("Content-Type", "application/json")

    private fun responseObject(body: String): JSONObject {
        require(isBoundedJson(body, objectOnly = true)) { "Cloud backup returned invalid JSON." }
        return JSONObject(body)
    }

    private fun responseArray(body: String): JSONArray {
        require(isBoundedJson(body)) { "Cloud backup returned invalid JSON." }
        return JSONArray(body)
    }

    private fun authorizedRequestBuilder(path: String, session: BackupSession): Request.Builder =
        requestBuilder(path).header("Authorization", "Bearer ${session.accessToken}")

    private suspend fun execute(
        request: Request,
        authRequest: Boolean = false,
        retryNetworkFailures: Boolean = true,
    ): String {
        var retryIndex = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                val result = executeOnce(request, authRequest)
                currentCoroutineContext().ensureActive()
                return result
            } catch (error: IOException) {
                if (!retryNetworkFailures || retryIndex >= networkRetryDelaysMs.size) {
                    throw IllegalStateException(
                        "Cloud backup network is unavailable. Check the connection and try again.",
                        error,
                    )
                }
                delay(networkRetryDelaysMs[retryIndex++].coerceAtLeast(0L))
            }
        }
    }

    /** Cancellation closes the socket even while response headers or body bytes are stalled. */
    private suspend fun executeOnce(request: Request, authRequest: Boolean): String =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use { readResponse(it, request, authRequest) }
                        if (!continuation.isCancelled) continuation.resume(text)
                    } catch (error: Exception) {
                        if (!continuation.isCancelled) continuation.resumeWithException(error)
                    }
                }
            })
        }

    private fun readResponse(response: Response, request: Request, authRequest: Boolean): String {
        if (!response.isSuccessful) {
            throw BackupHttpException(response.code,
                when {
                    authRequest && response.code in 400..499 -> "Email or password is incorrect."
                    response.code == 401 || response.code == 403 ->
                        "Cloud backup authorization expired. Sign in again."
                    response.code == 404 && request.url.encodedPath.contains("delete-account") ->
                        "Account deletion is not available on this server yet."
                    response.code == 404 -> "No cloud backup was found."
                    response.code == 429 -> "Cloud backup is temporarily busy. Try again shortly."
                    response.code >= 500 -> "Cloud backup service is temporarily unavailable."
                    else -> "Cloud backup request failed (${response.code})."
                },
            )
        }
        val body = response.body ?: return ""
        if (body.contentLength() > MAX_RESPONSE_BYTES) {
            throw IllegalStateException("Cloud backup response was too large.")
        }
        val source = body.source()
        if (source.request(MAX_RESPONSE_BYTES + 1L)) {
            throw IllegalStateException("Cloud backup response was too large.")
        }
        return source.readUtf8()
    }

    private fun ensureConfigured() {
        check(configuration.isConfigured) { "Cloud backup is not configured in this build." }
    }

    private fun validateCredentials(email: String, password: String) {
        require(email.isNotBlank() && password.isNotBlank()) { "Email and password are required." }
        require(email.length <= 320 && password.length <= 1_024) {
            "Email or password is too long."
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val REFRESH_EARLY_MS = 60_000L
        const val MAX_RESPONSE_BYTES = 12L * 1024L * 1024L
        const val MAX_TOKEN_CHARS = 131_072
        const val MAX_SESSION_SECONDS = 7L * 24L * 60L * 60L
    }
}
