package com.dailybeat.app.backup

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
        assertEquals("/rest/v1/dailybeat_backups?on_conflict=user_id", request.path)
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
    fun `http failures expose a safe message`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"message":"token access-one and refresh-one rejected"}""",
            ),
        )

        val message = client.download().exceptionOrNull()?.message.orEmpty()

        assertEquals("Cloud authorization expired. Sign in again.", message)
        assertFalse(message.contains("access-one"))
        assertFalse(message.contains("refresh-one"))
    }

    @Test
    fun `rejected token refresh clears the expired session`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 900_000L)
        server.enqueue(MockResponse().setResponseCode(400).setBody("{}"))

        val error = client.authenticatedSession().exceptionOrNull()

        assertTrue(error is BackupSessionExpiredException)
        assertEquals(null, sessions.current)
    }

    @Test
    fun `transient token refresh failure preserves the encrypted session`() = runBlocking {
        sessions.current = activeSession(expiresAtMs = 900_000L)
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        val error = client.authenticatedSession().exceptionOrNull()

        assertTrue(error is BackupTransientException)
        assertEquals("refresh-one", sessions.current?.refreshToken)
    }

    @Test
    fun `sign out revokes the server session and clears local tokens`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(MockResponse().setResponseCode(204))

        val result = client.revokeSession()

        assertTrue(result.isSuccess)
        assertEquals(null, sessions.current)
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", request.path)
        assertEquals("Bearer access-one", request.getHeader("Authorization"))
    }

    @Test
    fun `sign out clears local tokens even when revocation is temporarily unavailable`() = runBlocking {
        sessions.current = activeSession()
        server.enqueue(MockResponse().setResponseCode(503))

        val result = client.revokeSession()

        assertTrue(result.isFailure)
        assertEquals(null, sessions.current)
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
