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
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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
        server.enqueue(json("0"))
        server.enqueue(
            json(
                """[{"id":"mission-1","title":"Night sector","starts_at":"2026-09-01T16:30:00Z","ends_at":"2026-09-01T20:30:00Z","guidance":"suggested_route","instructions":"Check gates","status":"active","version":7,"route_geojson":{"type":"LineString","coordinates":[[77.4,12.9],[77.5,13.0]]},"updated_at":"2026-09-01T17:00:00Z","retention_until":null}]""",
            ),
        )
        server.enqueue(json("""[{"id":"priority-1","mission_id":"mission-1","name":"Bus stand","latitude":13.0,"longitude":77.5,"radius_m":35,"required":true,"sort_order":0}]"""))
        server.enqueue(json("[]"))
        server.enqueue(json("""[{"mission_id":"mission-1","user_id":"user-1"}]"""))
        server.enqueue(
            json(
                """[{"id":"update-2","mission_id":"mission-1","category":"review_context","detail":"Festival crowd required a diversion.","occurred_at":"2026-09-01T18:05:00Z","created_at":"2026-09-01T18:05:01Z","review_id":"review-1"},{"id":"update-1","mission_id":"mission-1","category":"operational_deviation","detail":"Crowd diversion","occurred_at":"2026-09-01T18:00:00Z","created_at":"2026-09-01T18:00:01Z","review_id":null}]""",
            ),
        )
        server.enqueue(
            json(
                """[{"id":"review-1","outcome":"needs_context","notes":"Explain the market diversion.","reviewed_at":"2026-09-01T18:02:00Z","created_at":"2026-09-01T18:02:00Z"}]""",
            ),
        )
        server.enqueue(json("""[{"latitude":13.01,"longitude":77.51},{"latitude":13.0,"longitude":77.5}]"""))
        server.enqueue(countResponse(24))
        server.enqueue(countResponse(3))

        val snapshot = client.loadSnapshot("mission-1").getOrThrow()

        assertEquals("Night sector", snapshot.missions.single().title)
        assertEquals(PatrolMissionStatus.ACTIVE, snapshot.missions.single().status)
        assertEquals(7, snapshot.missions.single().version)
        assertEquals(1_788_294_600_000L, snapshot.missions.single().endsAtEpochMs)
        assertEquals(null, snapshot.missions.single().retentionUntilEpochMs)
        assertTrue(snapshot.missions.single().hasOperationalDeviation)
        assertEquals("Bus stand", snapshot.missions.single().priorityLocations.single().name)
        assertEquals(13.0, snapshot.missions.single().priorityLocations.single().latitude!!, 0.0)
        assertEquals(77.5, snapshot.missions.single().priorityLocations.single().longitude!!, 0.0)
        assertEquals(35.0, snapshot.missions.single().priorityLocations.single().radiusM!!, 0.0)
        assertEquals("mission-1", snapshot.evidenceMissionId)
        assertEquals(24, snapshot.recordedTrackPoints)
        assertEquals(3, snapshot.observationCount)
        assertEquals(2, snapshot.routePoints.size)
        assertEquals(13.0, snapshot.routePoints.first().latitude, 0.0)
        assertEquals(13.01, snapshot.routePoints.last().latitude, 0.0)
        assertEquals(2, snapshot.plannedRoutePoints.size)
        assertEquals(12.9, snapshot.plannedRoutePoints.first().latitude, 0.0)
        assertEquals(77.4, snapshot.plannedRoutePoints.first().longitude, 0.0)
        assertEquals(13.0, snapshot.plannedRoutePoints.last().latitude, 0.0)
        assertEquals(77.5, snapshot.plannedRoutePoints.last().longitude, 0.0)
        assertEquals("review-1", snapshot.reviewContextRequestId)
        assertEquals("Explain the market diversion.", snapshot.reviewContextRequest)
        assertEquals("Festival crowd required a diversion.", snapshot.reviewContextResponse)
        repeat(12) {
            val request = server.takeRequest()
            assertEquals("Bearer access-token", request.getHeader("Authorization"))
            if (request.path == "/rest/v1/rpc/patrolgrid_close_expired_sessions") {
                assertEquals("POST", request.method)
            }
            if (request.path.orEmpty().contains("patrolgrid_missions?select=")) {
                assertTrue(request.path.orEmpty().contains("version"))
                assertTrue(request.path.orEmpty().contains("route_geojson"))
                assertTrue(request.path.orEmpty().contains("retention_until"))
            }
            if (request.path.orEmpty().contains("patrolgrid_field_updates?select=")) {
                assertTrue(request.path.orEmpty().contains("category"))
            }
            if (request.path.orEmpty().contains("patrolgrid_track_points?select=latitude")) {
                assertTrue(request.path.orEmpty().contains("order=recorded_at.desc"))
            }
        }
    }

    @Test
    fun `mission retention clock is accepted only for terminal states`() {
        val deadline = "2027-09-01T20:30:00Z"

        assertEquals(null, parsePatrolMissionRetentionDeadline("assigned", ""))
        assertEquals(
            1_819_830_600_000L,
            parsePatrolMissionRetentionDeadline("completed", deadline),
        )
        assertTrue(
            runCatching { parsePatrolMissionRetentionDeadline("assigned", deadline) }
                .exceptionOrNull()?.message.orEmpty().contains("inconsistent"),
        )
        assertTrue(
            runCatching { parsePatrolMissionRetentionDeadline("needs_review", "") }
                .exceptionOrNull()?.message.orEmpty().contains("inconsistent"),
        )
        assertTrue(
            runCatching { parsePatrolMissionRetentionDeadline("completed", "not-a-clock") }
                .exceptionOrNull()?.message.orEmpty().contains("invalid"),
        )
    }

    @Test
    fun `authoritative terminal mission is never reactivated by local active id`() = runBlocking {
        enqueueIdentity()
        server.enqueue(json("0"))
        server.enqueue(
            json(
                """[{"id":"mission-1","title":"Night sector","starts_at":"2026-09-01T16:30:00Z","ends_at":"2026-09-01T20:30:00Z","guidance":"suggested_route","instructions":"Check gates","status":"needs_review","version":8,"route_geojson":{"type":"LineString","coordinates":[[77.4,12.9],[77.5,13.0]]},"updated_at":"2026-09-01T20:31:00Z","retention_until":"2027-09-01T20:30:00Z"}]""",
            ),
        )
        repeat(4) { server.enqueue(json("[]")) }
        server.enqueue(json("[]"))
        server.enqueue(json("[]"))
        server.enqueue(countResponse(0))
        server.enqueue(countResponse(0))

        val mission = client.loadSnapshot("mission-1").getOrThrow().missions.single()

        assertEquals(PatrolMissionStatus.NEEDS_REVIEW, mission.status)
        assertEquals(1_819_830_600_000L, mission.retentionUntilEpochMs)
    }

    @Test
    fun `route upload is idempotent and never sends encrypted session tokens in body`() = runBlocking {
        server.enqueue(json("1"))

        val result = client.uploadTrackPoints(
            missionId = "mission-1",
            sessionId = "session-1",
            points = listOf(
                RemoteTrackPoint("point-1", 7, 1_788_200_000_000L, 13.0, 77.5, 8f),
            ),
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_ingest_track_points", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_session\":\"session-1\""))
        assertTrue(body.contains("\"client_point_id\":\"point-1\""))
        assertFalse(body.contains("mission-1"))
        assertFalse(body.contains("user_id"))
        assertFalse(body.contains("access-token"))
        assertFalse(body.contains("refresh-token"))
    }

    @Test
    fun `priority and field evidence use server-derived ingestion workflows`() = runBlocking {
        server.enqueue(json("\"visit-1\""))
        server.enqueue(json("\"update-1\""))

        assertTrue(
            client.markPriorityVisited(
                sessionId = "session-1",
                priorityLocationId = "priority-1",
                clientVisitId = "visit-1",
                visitedAtMs = 1_788_200_000_000L,
            ).isSuccess,
        )
        assertTrue(
            client.addFieldUpdate(
                sessionId = "session-1",
                category = "observation",
                detail = "Gate checked",
                clientUpdateId = "update-1",
                occurredAtMs = 1_788_200_000_000L,
                reviewId = null,
            ).isSuccess,
        )

        val visit = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_record_priority_visit", visit.path)
        val visitBody = visit.body.readUtf8()
        assertTrue(visitBody.contains("\"target_session\":\"session-1\""))
        assertTrue(visitBody.contains("\"target_priority_location\":\"priority-1\""))
        assertFalse(visitBody.contains("mission_id"))
        assertFalse(visitBody.contains("user_id"))

        val update = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_record_field_update", update.path)
        val updateBody = update.body.readUtf8()
        assertTrue(updateBody.contains("\"target_session\":\"session-1\""))
        assertTrue(updateBody.contains("\"target_category\":\"observation\""))
        assertFalse(updateBody.contains("mission_id"))
        assertFalse(updateBody.contains("user_id"))
    }

    @Test
    fun `route upload rejects oversized batches before network`() = runBlocking {
        val points = (0..250).map { index ->
            RemoteTrackPoint("point-$index", index, 1_788_200_000_000L, 13.0, 77.5, 8f)
        }

        val result = client.uploadTrackPoints("mission-1", "session-1", points)

        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `ordinary evidence validation 400 is not treated as purged mission`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"code":"22023","message":"invalid recorded_at"}""",
            ),
        )

        val result = client.uploadTrackPoints(
            "mission-1",
            "session-1",
            listOf(RemoteTrackPoint("point-1", 1, 1_788_200_000_000L, 13.0, 77.5, 8f)),
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PatrolGridRemoteException)
        assertFalse(result.exceptionOrNull() is PatrolGridEvidenceUnavailableException)
    }

    @Test
    fun `server no-data SQLSTATE marks evidence destination permanently unavailable`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"code":"P0002","message":"Mission is unavailable"}""",
            ),
        )

        val result = client.uploadTrackPoints(
            "mission-1",
            "session-1",
            listOf(RemoteTrackPoint("point-1", 1, 1_788_200_000_000L, 13.0, 77.5, 8f)),
        )

        assertTrue(result.exceptionOrNull() is PatrolGridEvidenceUnavailableException)
    }

    @Test
    fun `generic evidence 404 is not authoritative enough to discard local mission`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"code":"PGRST202","message":"RPC not found in schema cache"}""",
            ),
        )

        val result = client.uploadTrackPoints(
            "mission-1",
            "session-1",
            listOf(RemoteTrackPoint("point-1", 1, 1_788_200_000_000L, 13.0, 77.5, 8f)),
        )

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is PatrolGridEvidenceUnavailableException)
    }

    @Test
    fun `bounded iterative route parser accepts supported geometry and rejects hostile nesting`() {
        val supported = JSONObject(
            """{"type":"MultiPolygon","coordinates":[[[[77.5,13.0],[77.6,13.0],[77.6,13.1],[77.5,13.0]]]]}""",
        )
        val hostile = JSONObject(
            """{"type":"LineString","coordinates":[[[[[77.5,13.0]]]]]}""",
        )
        val outOfRange = JSONObject(
            """{"type":"LineString","coordinates":[[181.0,13.0],[77.5,13.1]]}""",
        )

        assertEquals(4, PatrolRouteGeoJsonParser.parse(supported).size)
        assertTrue(PatrolRouteGeoJsonParser.parse(hostile).isEmpty())
        assertTrue(PatrolRouteGeoJsonParser.parse(outOfRange).isEmpty())
    }

    @Test
    fun `session start uses narrow rpc without client identity or timestamp`() = runBlocking {
        server.enqueue(json("\"session-server\""))

        val result = client.startSession(
            missionId = "mission-1",
            installationId = "50000000-0000-0000-0000-000000000001",
            appVersion = "1.0-test",
        )

        assertEquals("session-server", result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_start_session", request.path)
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_session\":"))
        assertTrue(body.contains("\"target_mission\":\"mission-1\""))
        assertTrue(body.contains("\"target_installation\":\"50000000-0000-0000-0000-000000000001\""))
        assertTrue(body.contains("\"target_app_version\":\"1.0-test\""))
        assertFalse(body.contains("started_at"))
        assertFalse(body.contains("user_id"))
    }

    @Test
    fun `session closure uses narrow rpc and never sends the offline client timestamp`() = runBlocking {
        server.enqueue(json("\"2026-09-01T20:30:00Z\""))

        val result = client.endSession(
            sessionId = "session-1",
            reason = "duty_window_ended",
            endedAtMs = 1_788_294_600_000L,
        )

        assertTrue(result.isSuccess)
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_end_session", request.path)
        assertEquals("POST", request.method)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_session\":\"session-1\""))
        assertTrue(body.contains("\"target_reason\":\"duty_window_ended\""))
        assertFalse(body.contains("ended_at"))
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
    fun `supervisor review uses version checked rpc and returns updated version`() = runBlocking {
        server.enqueue(json("8"))

        val result = client.submitReview(
            missionId = "mission-1",
            expectedVersion = 7,
            outcome = SupervisorReviewOutcome.NEEDS_CONTEXT,
            notes = "  Confirm the operational reason with the patrol unit.  ",
        )

        assertEquals(8, result.getOrThrow())
        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/patrolgrid_submit_review", request.path)
        assertTrue(request.getHeader("Prefer").orEmpty().contains("return=representation"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_mission\":\"mission-1\""))
        assertTrue(body.contains("\"target_expected_version\":7"))
        assertTrue(body.contains("\"target_outcome\":\"needs_context\""))
        assertTrue(body.contains("\"target_notes\":\"Confirm the operational reason with the patrol unit.\""))
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
