package com.dailybeat.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SupabaseBackupClientTest {

    private lateinit var server: MockWebServer
    private lateinit var sessions: MemorySessionStore
    private lateinit var client: SupabaseBackupClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        sessions = MemorySessionStore()
        client = SupabaseBackupClient(
            configuration = BackupConfiguration(server.url("/").toString(), "public-anon-key"),
            sessionStore = sessions,
            httpClient = OkHttpClient(),
            clock = { 1_000_000L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `archive calls bind UUID and index and cleanup only uncommitted version`() = runBlocking {
        sessions.current = activeSession()
        repeat(4) { server.enqueue(MockResponse().setResponseCode(204)) }
        val id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        client.beginVersion(id)
        client.uploadPart(id, 0, "{\"nonce\":\"abc\",\"ciphertext\":\"cipher\"}")
        client.publishVersion(id, "{\"format\":\"dailybeat-archive\"}", 1)
        client.abandonVersion(id)
        assertEquals("/rest/v1/rpc/begin_dailybeat_backup", server.takeRequest().path)
        val part = server.takeRequest()
        assertEquals("/rest/v1/rpc/put_dailybeat_backup_part", part.path)
        val json = org.json.JSONObject(part.body.readUtf8())
        assertEquals(id, json.getString("backup_id"))
        assertEquals(0, json.getInt("part_index"))
        assertEquals("Bearer access-one", part.getHeader("Authorization"))
        assertEquals("/rest/v1/rpc/publish_dailybeat_backup", server.takeRequest().path)
        assertEquals("/rest/v1/dailybeat_backup_versions?id=eq.$id&manifest=is.null", server.takeRequest().path)
    }

    @Test
    fun `temporary refresh failure keeps session for retry`() = runBlocking {
        sessions.current = activeSession().copy(expiresAtMs=0)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(client.download().isFailure)
        assertEquals("refresh-one", sessions.current?.refreshToken)
    }

    @Test
    fun `sign in saves session without persisting password`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"access_token":"access-one","refresh_token":"refresh-one","expires_in":3600,"user":{"id":"user-1","email":"person@example.com"}}""",
            ),
        )

        val result = client.signIn("person@example.com", "correct horse")

        assertTrue(result.isSuccess)
        assertEquals("user-1", sessions.current?.userId)
        assertEquals("person@example.com", sessions.current?.email)
        assertFalse(sessions.toString().contains("correct horse"))
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals("public-anon-key", request.getHeader("apikey"))
    }

    @Test
    fun `sign in retries transient network lookup failures`() = runBlocking {
        val attempts = AtomicInteger()
        val retryingClient = SupabaseBackupClient(
            configuration = BackupConfiguration(server.url("/").toString(), "public-anon-key"),
            sessionStore = sessions,
            httpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    if (attempts.incrementAndGet() < 3) {
                        throw UnknownHostException("synthetic lookup failure")
                    }
                    chain.proceed(chain.request())
                }
                .build(),
            clock = { 1_000_000L },
            networkRetryDelaysMs = listOf(0L, 0L),
        )
        server.enqueue(
            jsonResponse(
                """{"access_token":"access-one","refresh_token":"refresh-one","expires_in":3600,"user":{"id":"user-1","email":"person@example.com"}}""",
            ),
        )

        val result = retryingClient.signIn("person@example.com", "correct horse")

        assertTrue(result.isSuccess)
        assertEquals(3, attempts.get())
        assertEquals("user-1", sessions.current?.userId)
    }

    @Test
    fun `sign up reports email confirmation without storing password`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"id":"user-2","email":"new@example.com","confirmation_sent_at":"2026-08-31T01:00:00Z"}""",
            ),
        )

        val result = client.signUp("new@example.com", "new password").getOrThrow()

        assertTrue(result.requiresEmailConfirmation)
        assertEquals(null, result.session)
        assertEquals(null, sessions.current)
        val request = server.takeRequest()
        assertEquals("/auth/v1/signup", request.path)
        assertFalse(sessions.toString().contains("new password"))
    }

    @Test
    fun `upload sends authenticated snapshot for current user`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(MockResponse().setResponseCode(201).setBody("[]"))

        val result = client.upload("""{"schemaVersion":1}""")

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/rest/v1/dailybeat_encrypted_backups?on_conflict=user_id", request.path)
        assertEquals("Bearer access-one", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"user_id\":\"user-1\""))
        assertTrue(body.contains("\"snapshot\""))
    }

    @Test
    fun `download returns remote snapshot and timestamp`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(
            jsonResponse(
                """[{"snapshot":{"schemaVersion":1,"events":[]},"updated_at":"2026-08-31T01:02:03Z"}]""",
            ),
        )

        val remote = client.download().getOrThrow()

        assertEquals("2026-08-31T01:02:03Z", remote?.updatedAt)
        assertTrue(remote!!.snapshotJson.contains("\"schemaVersion\":1"))
    }

    @Test
    fun `deeply nested authentication replies fail without retaining a session`() = runBlocking {
        val nested = "{\"unexpected\":" + "[".repeat(5_000) + "0" + "]".repeat(5_000) + "}"
        repeat(2) { server.enqueue(jsonResponse(nested)) }

        val signIn = client.signIn("person@example.com", "correct horse")
        val signUp = client.signUp("person@example.com", "correct horse")

        for (result in listOf(signIn, signUp)) {
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Cloud backup returned invalid JSON.", result.exceptionOrNull()?.message)
        }
        assertEquals(null, sessions.current)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `deeply nested download and archive replies fail before recursive parsing`() = runBlocking {
        sessions.current = activeSession()
        val nested = "[".repeat(5_000) + "0" + "]".repeat(5_000)
        repeat(3) { server.enqueue(jsonResponse(nested)) }

        val results = listOf(
            client.download(),
            runCatching { client.versions() },
            runCatching { client.downloadPart("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", 0) },
        )

        for (result in results) {
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Cloud backup returned invalid JSON.", result.exceptionOrNull()?.message)
        }
        assertEquals(activeSession(), sessions.current)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `expired session refreshes before upload`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 900_000L)
        server.enqueue(
            jsonResponse(
                """{"access_token":"access-two","refresh_token":"refresh-two","expires_in":3600,"user":{"id":"user-1","email":"person@example.com"}}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(201).setBody("[]"))

        assertTrue(client.upload("""{"schemaVersion":1}""").isSuccess)

        assertEquals("/auth/v1/token?grant_type=refresh_token", server.takeRequest().path)
        assertEquals("Bearer access-two", server.takeRequest().getHeader("Authorization"))
        assertEquals("access-two", sessions.current?.accessToken)
    }

    @Test
    fun `late refresh cannot restore a signed out session`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.contains("grant_type=refresh_token") == true) {
                    client.signOut()
                    return refreshedSessionResponse()
                }
                return jsonResponse("[]")
            }
        }

        assertTrue(client.download().isFailure)

        assertEquals(null, client.currentSession())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `late sign in cannot undo sign out`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                client.signOut()
                return refreshedSessionResponse()
            }
        }

        assertTrue(client.signIn("person@example.com", "correct horse").isFailure)
        assertEquals(null, client.currentSession())
    }

    @Test
    fun `sign out invalidates sign in waiting for IO dispatch`() = assertQueuedAuthenticationIsInvalidated(false)

    @Test
    fun `sign out invalidates sign up waiting for IO dispatch`() = assertQueuedAuthenticationIsInvalidated(true)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun assertQueuedAuthenticationIsInvalidated(signUp: Boolean) = runTest {
        sessions.current = activeSession()
        server.enqueue(refreshedSessionResponse())
        val queued = SupabaseBackupClient(
            BackupConfiguration(server.url("/").toString(), "public-anon-key"), sessions,
            authenticationDispatcher = StandardTestDispatcher(testScheduler),
        )
        // Enter authentication immediately, then hold the IO body until after sign-out.
        val pending = async(UnconfinedTestDispatcher(testScheduler)) {
            if (signUp) queued.signUp("person@example.com", "test password").isFailure
            else queued.signIn("person@example.com", "test password").isFailure
        }
        assertFalse(pending.isCompleted)
        queued.signOut()

        assertTrue(pending.await())
        assertEquals(null, sessions.current)
        assertEquals("A revoked queued request must not transmit credentials", 0, server.requestCount)
    }

    @Test
    fun `concurrent cloud requests rotate an expired refresh token only once`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 0)
        val refreshes = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.contains("grant_type=refresh_token") == true) {
                    refreshes.incrementAndGet()
                    refreshedSessionResponse().setBodyDelay(150, TimeUnit.MILLISECONDS)
                } else {
                    jsonResponse("[]")
                }
        }

        val results = (1..8).map { async(Dispatchers.Default) { client.download() } }.awaitAll()

        assertTrue(results.all { it.isSuccess })
        assertEquals(1, refreshes.get())
        assertEquals("refresh-two", client.currentSession()?.refreshToken)
    }

    @Test
    fun `failed old refresh does not clear a newer session`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 0)
        val replacement = activeSession().copy(userId = "user-2", accessToken = "new-account")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                client.signOut()
                sessions.current = replacement
                return MockResponse().setResponseCode(401)
            }
        }

        assertTrue(client.download().isFailure)
        assertEquals(replacement, client.currentSession())
    }

    @Test
    fun `refresh cannot silently change account identity`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 0)
        server.enqueue(jsonResponse(
            """{"access_token":"access-two","refresh_token":"refresh-two","expires_in":3600,"user":{"id":"different-user","email":"other@example.com"}}""",
        ))

        assertTrue(client.download().isFailure)

        assertEquals("user-1", client.currentSession()?.userId)
        assertEquals("access-one", client.currentSession()?.accessToken)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `http failures expose a safe message`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"message":"token access-one and refresh-one rejected"}""",
            ),
        )

        val message = client.download().exceptionOrNull()?.message.orEmpty()

        assertEquals("Cloud backup authorization expired. Sign in again.", message)
        assertFalse(message.contains("access-one"))
        assertFalse(message.contains("refresh-one"))
    }

    @Test
    fun `cloud data deletion removes encrypted and legacy rows for current user only`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(client.deleteCloudData().isSuccess)

        val archive = server.takeRequest()
        assertEquals("/rest/v1/dailybeat_backup_versions?user_id=eq.user-1", archive.path)
        val encrypted = server.takeRequest()
        val legacy = server.takeRequest()
        assertEquals("DELETE", encrypted.method)
        assertEquals("/rest/v1/dailybeat_encrypted_backups?user_id=eq.user-1", encrypted.path)
        assertEquals("Bearer access-one", encrypted.getHeader("Authorization"))
        assertEquals("/rest/v1/dailybeat_backups?user_id=eq.user-1", legacy.path)
    }

    @Test
    fun `account deletion calls edge function and clears local session only after success`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(jsonResponse("{\"message\":\"Account deleted.\"}"))

        assertTrue(client.deleteAccount().isSuccess)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/functions/v1/delete-account", request.path)
        assertEquals("Bearer access-one", request.getHeader("Authorization"))
        assertEquals(null, sessions.current)
    }

    @Test
    fun `failed account deletion keeps session so the user can retry`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(MockResponse().setResponseCode(500).setBody("secret server detail"))

        assertTrue(client.deleteAccount().isFailure)

        assertEquals("user-1", sessions.current?.userId)
    }

    @Test
    fun `injected shared client cannot redirect backup credentials`() = runBlocking {
        sessions.current = activeSession()
        MockWebServer().use { redirected ->
            redirected.start()
            server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", redirected.url("/collect")))
            redirected.enqueue(jsonResponse("[]"))

            assertTrue(client.download().isFailure)

            assertEquals(1, server.requestCount)
            assertEquals(0, redirected.requestCount)
            assertEquals("Bearer access-one", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `cancelling sign in closes a stalled call without retries or session changes`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val failed = CountDownLatch(1)
        val network = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callFailed(call: Call, ioe: IOException) { failed.countDown() }
        }).build()
        val cancellable = SupabaseBackupClient(
            BackupConfiguration(server.url("/").toString(), "public-anon-key"), sessions, network,
            networkRetryDelaysMs = listOf(0L, 0L),
        )
        val job = launch(Dispatchers.IO) { cancellable.signIn("person@example.com", "test password") }
        try {
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue("Cancellation must close the socket", failed.await(5, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
            assertEquals(null, sessions.current)
        } finally { job.cancelAndJoin() }
    }

    @Test
    fun `cancelling backup download closes a stalled response body`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(jsonResponse("[]").setBodyDelay(2, TimeUnit.SECONDS))
        val headers = CountDownLatch(1)
        val failed = CountDownLatch(1)
        val network = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun responseHeadersEnd(call: Call, response: okhttp3.Response) { headers.countDown() }
            override fun callFailed(call: Call, ioe: IOException) { failed.countDown() }
        }).build()
        val cancellable = SupabaseBackupClient(
            BackupConfiguration(server.url("/").toString(), "public-anon-key"), sessions, network,
            clock = { 1_000_000L }, networkRetryDelaysMs = listOf(0L, 0L),
        )
        val job = launch(Dispatchers.IO) { cancellable.download() }
        try {
            assertTrue(headers.await(5, TimeUnit.SECONDS))
            withTimeout(5_000) { job.cancelAndJoin() }
            assertTrue("Cancellation must close the body read", failed.await(5, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
            assertEquals(activeSession(), sessions.current)
        } finally { job.cancelAndJoin() }
    }

    private fun activeSession(expiresAtMs: Long = 5_000_000L) = BackupSession(
        userId = "user-1",
        email = "person@example.com",
        accessToken = "access-one",
        refreshToken = "refresh-one",
        expiresAtMs = expiresAtMs,
    )

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun refreshedSessionResponse() = jsonResponse(
        """{"access_token":"access-two","refresh_token":"refresh-two","expires_in":3600,"user":{"id":"user-1","email":"person@example.com"}}""",
    )

    private class MemorySessionStore : BackupSessionStore {
        var current: BackupSession? = null

        override fun get(): BackupSession? = current

        override fun save(session: BackupSession) {
            current = session
        }

        override fun clear() {
            current = null
        }

        override fun toString(): String = "MemorySessionStore(hasSession=${current != null})"
    }
}
