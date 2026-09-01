package com.dailybeat.app.patrolgrid

import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupRemote
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.backup.BackupSignUpResult
import com.dailybeat.app.backup.RemoteBackup
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRouteGuidance
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
class SupabasePatrolGridClientTest {
    private lateinit var server: MockWebServer
    private lateinit var sessions: FakeBackupRemote
    private lateinit var client: SupabasePatrolGridClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        sessions = FakeBackupRemote()
        client = SupabasePatrolGridClient(
            configuration = BackupConfiguration(server.url("/").toString(), "anon-key"),
            sessionRemote = sessions,
            httpClient = OkHttpClient(),
            clock = { 1_788_200_000_000L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sign in resolves role and subdivision from server membership`() = runBlocking {
        server.enqueue(json("""[{"subdivision_id":"sub-1","role":"supervisor","display_name":"Officer One","badge_number":"B-17"}]"""))
        server.enqueue(json("""[{"name":"Central Subdivision"}]"""))

        val identity = client.signIn("officer@example.com", "secret-password").getOrThrow()

        assertEquals(PatrolRole.SUPERVISOR, identity.role)
        assertEquals("Central Subdivision", identity.subdivisionName)
        assertEquals("Officer One", identity.displayName)
        assertEquals(1, sessions.signInCalls)
        assertTrue(server.takeRequest().path.orEmpty().contains("user_id=eq.user-1"))
    }

    @Test
    fun `account without active membership is denied after authentication`() = runBlocking {
        server.enqueue(json("[]"))

        val result = client.signIn("officer@example.com", "secret-password")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not assigned"))
        assertTrue(sessions.signedOut)
    }

    @Test
    fun `snapshot maps server mission priorities and counts`() = runBlocking {
        enqueueIdentity()
        server.enqueue(
            json(
                """[{"id":"mission-1","title":"Night sector","starts_at":"2026-09-01T16:30:00Z","ends_at":"2026-09-01T20:30:00Z","guidance":"suggested_route","instructions":"Check gates","status":"assigned","updated_at":"2026-09-01T17:00:00Z"}]""",
            ),
        )
        server.enqueue(json("""[{"id":"priority-1","mission_id":"mission-1","name":"Bus stand","required":true,"sort_order":0}]"""))
        server.enqueue(json("[]"))
        server.enqueue(json("""[{"mission_id":"mission-1","user_id":"user-1"}]"""))
        server.enqueue(json("""[{"latitude":13.01,"longitude":77.51},{"latitude":13.0,"longitude":77.5}]"""))
        server.enqueue(countResponse(24))
        server.enqueue(countResponse(3))

        val snapshot = client.loadSnapshot("mission-1").getOrThrow()

        assertEquals("Night sector", snapshot.missions.single().title)
        assertEquals(PatrolMissionStatus.ACTIVE, snapshot.missions.single().status)
        assertEquals("Bus stand", snapshot.missions.single().priorityLocations.single().name)
        assertEquals(24, snapshot.recordedTrackPoints)
        assertEquals(3, snapshot.observationCount)
        assertEquals(2, snapshot.routePoints.size)
        assertEquals(13.0, snapshot.routePoints.first().latitude, 0.0)
        assertEquals(13.01, snapshot.routePoints.last().latitude, 0.0)
        repeat(9) {
            val request = server.takeRequest()
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            if (request.path.orEmpty().contains("patrolgrid_track_points?select=latitude")) {
                assertTrue(request.path.orEmpty().contains("order=recorded_at.desc"))
            }
        }
    }

    @Test
    fun `route upload is idempotent and never sends encrypted session tokens in body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val result = client.uploadTrackPoints(
            missionId = "mission-1",
            sessionId = "session-1",
            points = listOf(
                RemoteTrackPoint("point-1", 7, 1_788_200_000_000L, 13.0, 77.5, 8f),
            ),
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("on_conflict=user_id,client_point_id"))
        assertTrue(request.getHeader("Prefer").orEmpty().contains("resolution=ignore-duplicates"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"client_point_id\":\"point-1\""))
        assertFalse(body.contains("access-token"))
        assertFalse(body.contains("refresh-token"))
    }

    @Test
    fun `assignment options come from active server routes and staffed units`() = runBlocking {
        server.enqueue(json("""[{"id":"route-1","name":"Night sector","default_start_time":"22:00:00","default_duration_minutes":240}]"""))
        server.enqueue(json("""[{"route_template_id":"route-1","name":"Bus stand","sort_order":0}]"""))
        server.enqueue(json("""[{"id":"unit-1","name":"Unit 12"}]"""))
        server.enqueue(json("""[{"unit_id":"unit-1","user_id":"user-1"},{"unit_id":"unit-1","user_id":"user-2"}]"""))

        val options = client.loadAssignmentOptions().getOrThrow()

        assertEquals("Night sector", options.routes.single().title)
        assertEquals("22:00–02:00", options.routes.single().dutyWindow)
        assertEquals(listOf("Bus stand"), options.routes.single().priorityLocations)
        assertEquals("unit-1", options.units.single().id)
        assertEquals(2, options.units.single().personnelCount)
    }

    @Test
    fun `assignment uses atomic server rpc`() = runBlocking {
        server.enqueue(json("\"mission-1\""))

        val result = client.createAssignment(
            PatrolAssignmentDraft(
                routePlanId = "route-1",
                unitName = "Unit 12",
                personnelCount = 2,
                guidance = PatrolRouteGuidance.AREA_COVERAGE,
                unitId = "unit-1",
            ),
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_create_assignment", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_route_template\":\"route-1\""))
        assertTrue(body.contains("\"target_unit\":\"unit-1\""))
        assertTrue(body.contains("\"target_guidance\":\"area_coverage\""))
    }

    @Test
    fun `authorization failure signs out and exposes safe message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"message":"access-token and refresh-token rejected"}""",
            ),
        )

        val result = client.loadIdentity()

        assertTrue(result.isFailure)
        assertEquals("Your PatrolGrid session expired. Sign in again.", result.exceptionOrNull()?.message)
        assertTrue(sessions.signedOut)
        assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("access-token"))
    }

    private fun enqueueIdentity() {
        server.enqueue(json("""[{"subdivision_id":"sub-1","role":"patrol","display_name":"Patrol One","badge_number":null}]"""))
        server.enqueue(json("""[{"name":"Central Subdivision"}]"""))
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun countResponse(count: Int) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Range", "0-0/$count")
        .setBody("[]")

    private class FakeBackupRemote : BackupRemote {
        private val session = BackupSession(
            userId = "user-1",
            email = "officer@example.com",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresAtMs = Long.MAX_VALUE,
        )
        var signInCalls = 0
        var signedOut = false

        override val isConfigured: Boolean = true
        override fun currentSession(): BackupSession? = if (signedOut) null else session
        override suspend fun authenticatedSession(): Result<BackupSession> = Result.success(session)
        override suspend fun signUp(email: String, password: String): Result<BackupSignUpResult> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signIn(email: String, password: String): Result<BackupSession> {
            signInCalls += 1
            signedOut = false
            return Result.success(session)
        }
        override suspend fun upload(snapshotJson: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun download(): Result<RemoteBackup?> =
            Result.failure(UnsupportedOperationException())
        override fun signOut() {
            signedOut = true
        }
    }
}
