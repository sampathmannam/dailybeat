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
        server.enqueue(
            json(
                """[{"id":"priority-1","mission_id":"mission-1","name":"Bus stand","latitude":13.0,"longitude":77.5,"radius_m":35,"required":true,"sort_order":0},{"id":"priority-2","mission_id":"mission-1","name":"East gate","latitude":13.1,"longitude":77.6,"radius_m":40,"required":true,"sort_order":1}]""",
            ),
        )
        server.enqueue(
            json(
                """[{"id":"visit-2","session_id":"session-closed","mission_id":"mission-1","priority_location_id":"priority-2","user_id":"user-1","visited_at":"2026-09-01T19:10:00Z","created_at":"2026-09-01T19:10:02Z","method":"gps","accuracy_m":7.25,"note":""},{"id":"visit-1","session_id":"session-open","mission_id":"mission-1","priority_location_id":"priority-1","user_id":"user-1","visited_at":"2026-09-01T18:00:00Z","created_at":"2026-09-01T18:00:03Z","method":"manual_with_context","accuracy_m":null,"note":"Gate access was restricted."}]""",
            ),
        )
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
        server.enqueue(
            json(
                """[{"session_id":"session-closed","mission_id":"mission-1","user_id":"user-1","display_name":"Patrol One","badge_number":null,"started_at":"2026-09-01T19:00:00Z","ended_at":"2026-09-01T19:30:00Z","end_reason":"device_issue","app_version":"1.0.0","track_point_count":0,"first_recorded_at":null,"last_recorded_at":null,"first_received_at":null,"last_received_at":null,"best_accuracy_m":null,"worst_accuracy_m":null},{"session_id":"session-open","mission_id":"mission-1","user_id":"user-1","display_name":"Patrol One","badge_number":"B-17","started_at":"2026-09-01T17:00:00Z","ended_at":null,"end_reason":null,"app_version":"1.1.0","track_point_count":2,"first_recorded_at":"2026-09-01T17:01:00Z","last_recorded_at":"2026-09-01T17:02:00Z","first_received_at":"2026-09-01T17:01:03Z","last_received_at":"2026-09-01T17:02:04Z","best_accuracy_m":6.5,"worst_accuracy_m":12.0}]""",
            ),
        )
        server.enqueue(
            json(
                """[{"session_id":"session-open","latitude":13.01,"longitude":77.51},{"session_id":"session-open","latitude":13.0,"longitude":77.5}]""",
            ),
        )
        server.enqueue(countResponse(24))
        server.enqueue(countResponse(3))

        val snapshot = client.loadSnapshot("mission-1").getOrThrow()

        assertEquals("Night sector", snapshot.missions.single().title)
        assertEquals(PatrolMissionStatus.ACTIVE, snapshot.missions.single().status)
        assertEquals(7, snapshot.missions.single().version)
        assertEquals(1_788_294_600_000L, snapshot.missions.single().endsAtEpochMs)
        assertEquals(null, snapshot.missions.single().retentionUntilEpochMs)
        assertTrue(snapshot.missions.single().hasOperationalDeviation)
        assertEquals("Bus stand", snapshot.missions.single().priorityLocations.first().name)
        assertEquals(13.0, snapshot.missions.single().priorityLocations.first().latitude!!, 0.0)
        assertEquals(77.5, snapshot.missions.single().priorityLocations.first().longitude!!, 0.0)
        assertEquals(35.0, snapshot.missions.single().priorityLocations.first().radiusM!!, 0.0)
        assertEquals("mission-1", snapshot.evidenceMissionId)
        assertEquals(24, snapshot.recordedTrackPoints)
        assertEquals(3, snapshot.observationCount)
        assertEquals(2, snapshot.routePoints.size)
        assertEquals(13.0, snapshot.routePoints.first().latitude, 0.0)
        assertEquals(13.01, snapshot.routePoints.last().latitude, 0.0)
        assertEquals(2, snapshot.evidenceSources.size)
        assertEquals("session-open", snapshot.selectedEvidenceSessionId)
        assertEquals("B-17", snapshot.evidenceSources.last().badgeNumber)
        assertEquals(2, snapshot.evidenceSources.last().trackPointCount)
        assertEquals(6.5f, snapshot.evidenceSources.last().bestAccuracyM)
        assertEquals(12.0f, snapshot.evidenceSources.last().worstAccuracyM)
        assertEquals(2, snapshot.priorityVisitEvidence.size)
        val openVisit = snapshot.priorityVisitEvidence.single { it.sessionId == "session-open" }
        assertEquals("Bus stand", openVisit.priorityName)
        assertEquals("Patrol One", openVisit.displayName)
        assertEquals("manual_with_context", openVisit.method)
        assertEquals("Gate access was restricted.", openVisit.note)
        val closedVisit = snapshot.priorityVisitEvidence.single { it.sessionId == "session-closed" }
        assertEquals("East gate", closedVisit.priorityName)
        assertEquals("gps", closedVisit.method)
        assertEquals(7.25f, closedVisit.accuracyM)
        assertEquals(2, snapshot.plannedRoutePoints.size)
        assertEquals(12.9, snapshot.plannedRoutePoints.first().latitude, 0.0)
        assertEquals(77.4, snapshot.plannedRoutePoints.first().longitude, 0.0)
        assertEquals(13.0, snapshot.plannedRoutePoints.last().latitude, 0.0)
        assertEquals(77.5, snapshot.plannedRoutePoints.last().longitude, 0.0)
        assertEquals("review-1", snapshot.reviewContextRequestId)
        assertEquals("Explain the market diversion.", snapshot.reviewContextRequest)
        assertEquals("Festival crowd required a diversion.", snapshot.reviewContextResponse)
        repeat(13) {
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
            if (request.path.orEmpty().contains("patrolgrid_priority_visits?select=")) {
                assertTrue(request.path.orEmpty().contains("session_id"))
                assertTrue(request.path.orEmpty().contains("created_at"))
                assertTrue(request.path.orEmpty().contains("method"))
            }
            if (request.path.orEmpty().contains("patrolgrid_evidence_session_summaries?select=")) {
                assertTrue(request.path.orEmpty().contains("track_point_count"))
                assertTrue(request.path.orEmpty().contains("order=started_at.desc"))
            }
            if (request.path.orEmpty().contains("patrolgrid_track_points?select=session_id")) {
                assertTrue(request.path.orEmpty().contains("order=recorded_at.desc"))
                assertTrue(request.path.orEmpty().contains("session_id=eq.session-open"))
                assertFalse(request.path.orEmpty().contains("mission_id="))
            }
        }
    }

    @Test
    fun `evidence trail reads one exact session and rejects mixed or invalid coordinates`() = runBlocking {
        server.enqueue(
            json(
                """[{"session_id":"session-1","latitude":13.01,"longitude":77.51},{"session_id":"session-1","latitude":13.0,"longitude":77.5}]""",
            ),
        )

        val trail = client.loadEvidenceTrail("session-1").getOrThrow()

        assertEquals("session-1", trail.sessionId)
        assertEquals(2, trail.routePoints.size)
        assertEquals(13.0, trail.routePoints.first().latitude, 0.0)
        assertEquals(13.01, trail.routePoints.last().latitude, 0.0)
        val exactSessionRequest = server.takeRequest()
        assertTrue(exactSessionRequest.path.orEmpty().contains("session_id=eq.session-1"))
        assertFalse(exactSessionRequest.path.orEmpty().contains("mission_id="))

        server.enqueue(json("""[{"session_id":"session-2","latitude":13.0,"longitude":77.5}]"""))
        val mixed = client.loadEvidenceTrail("session-1")
        assertTrue(mixed.isFailure)
        assertTrue(mixed.exceptionOrNull()?.message.orEmpty().contains("different evidence source"))
        server.takeRequest()

        server.enqueue(json("""[{"session_id":"session-1","latitude":91.0,"longitude":77.5}]"""))
        val invalid = client.loadEvidenceTrail("session-1")
        val invalidRequest = server.takeRequest()
        assertTrue(invalid.isFailure)
        assertTrue(invalid.exceptionOrNull()?.message.orEmpty().contains("invalid recorded location"))
        assertTrue(invalidRequest.path.orEmpty().contains("session_id=eq.session-1"))
    }

    @Test
    fun `supervisor snapshot selects only the latest session trail`() = runBlocking {
        server.enqueue(json("""[{"subdivision_id":"sub-1","role":"supervisor","display_name":"Supervisor One","badge_number":"S-1"}]"""))
        server.enqueue(json("""[{"name":"Central Subdivision"}]"""))
        server.enqueue(json("0"))
        server.enqueue(
            json(
                """[{"id":"mission-1","title":"Night sector","starts_at":"2026-09-01T16:30:00Z","ends_at":"2026-09-01T20:30:00Z","guidance":"suggested_route","instructions":"Check gates","status":"active","version":7,"route_geojson":null,"updated_at":"2026-09-01T17:00:00Z","retention_until":null}]""",
            ),
        )
        repeat(5) { server.enqueue(json("[]")) }
        server.enqueue(
            json(
                """[{"session_id":"session-latest","mission_id":"mission-1","user_id":"user-2","display_name":"Patrol Two","badge_number":"B-2","started_at":"2026-09-01T18:00:00Z","ended_at":null,"end_reason":null,"app_version":"1.1.0","track_point_count":1,"first_recorded_at":"2026-09-01T18:01:00Z","last_recorded_at":"2026-09-01T18:01:00Z","first_received_at":"2026-09-01T18:01:01Z","last_received_at":"2026-09-01T18:01:01Z","best_accuracy_m":8.0,"worst_accuracy_m":8.0},{"session_id":"session-older","mission_id":"mission-1","user_id":"user-3","display_name":"Patrol Three","badge_number":"B-3","started_at":"2026-09-01T17:00:00Z","ended_at":"2026-09-01T17:30:00Z","end_reason":"relieved","app_version":"1.1.0","track_point_count":1,"first_recorded_at":"2026-09-01T17:01:00Z","last_recorded_at":"2026-09-01T17:01:00Z","first_received_at":"2026-09-01T17:01:01Z","last_received_at":"2026-09-01T17:01:01Z","best_accuracy_m":9.0,"worst_accuracy_m":9.0}]""",
            ),
        )
        server.enqueue(json("""[{"session_id":"session-latest","latitude":13.0,"longitude":77.5}]"""))
        server.enqueue(countResponse(2))
        server.enqueue(countResponse(0))

        val snapshot = client.loadSnapshot("mission-1").getOrThrow()

        assertEquals("session-latest", snapshot.selectedEvidenceSessionId)
        assertEquals(listOf("session-latest", "session-older"), snapshot.evidenceSources.map { it.sessionId })
        assertEquals(1, snapshot.routePoints.size)
        val requests = (0 until 13).map { server.takeRequest() }
        val routeRequest = requests.single {
            it.path.orEmpty().contains("patrolgrid_track_points?select=session_id")
        }
        assertTrue(routeRequest.path.orEmpty().contains("session_id=eq.session-latest"))
        assertFalse(routeRequest.path.orEmpty().contains("session-older"))
        assertFalse(routeRequest.path.orEmpty().contains("mission_id="))
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
    fun `snapshot pagination never silently drops a full server page`() = runBlocking {
        val firstPriorityPage = (0 until 500).joinToString(prefix = "[", postfix = "]") { index ->
            """{"id":"priority-$index","mission_id":"mission-1","name":"Point $index","latitude":13.0,"longitude":77.5,"radius_m":35,"required":true,"sort_order":$index}"""
        }
        enqueueIdentity()
        server.enqueue(json("0"))
        server.enqueue(
            json(
                """[{"id":"mission-1","title":"Large route","starts_at":"2026-09-01T16:30:00Z","ends_at":"2026-09-01T20:30:00Z","guidance":"suggested_route","instructions":"","status":"active","version":1,"route_geojson":{},"updated_at":"2026-09-01T17:00:00Z","retention_until":null}]""",
            ),
        )
        server.enqueue(json(firstPriorityPage))
        server.enqueue(
            json(
                """[{"id":"priority-500","mission_id":"mission-1","name":"Point 500","latitude":13.0,"longitude":77.5,"radius_m":35,"required":true,"sort_order":500}]""",
            ),
        )
        server.enqueue(json("[]"))
        server.enqueue(json("""[{"mission_id":"mission-1","user_id":"user-1"}]"""))
        server.enqueue(json("[]"))
        server.enqueue(json("[]"))
        server.enqueue(json("[]"))
        server.enqueue(countResponse(0))
        server.enqueue(countResponse(0))

        val snapshot = client.loadSnapshot("mission-1").getOrThrow()

        assertEquals(501, snapshot.missions.single().priorityLocations.size)
        val priorityRequests = (0 until 13)
            .map { server.takeRequest().path.orEmpty() }
            .filter { "patrolgrid_priority_locations" in it }
        assertEquals(2, priorityRequests.size)
        assertTrue(priorityRequests.first().contains("limit=500&offset=0"))
        assertTrue(priorityRequests.last().contains("limit=500&offset=500"))
    }

    @Test
    fun `server quotas are non transient and direct staff to the command chain`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"code":"54000","message":"Patrol session restart rate limit exceeded"}""",
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"code":"54000","message":"Track assignment point limit exceeded"}""",
            ),
        )

        val start = client.startSession(
            missionId = "mission-1",
            installationId = "50000000-0000-0000-0000-000000000001",
            appVersion = "1.0-test",
        )
        val upload = client.uploadTrackPoints(
            missionId = "mission-1",
            sessionId = "session-1",
            points = listOf(
                RemoteTrackPoint("point-1", 1, 1_788_200_000_000L, 13.0, 77.5, 8f),
            ),
        )

        assertTrue(start.isFailure)
        assertFalse(start.exceptionOrNull() is PatrolGridTransientException)
        assertTrue(start.exceptionOrNull()?.message.orEmpty().contains("Wait 15 minutes"))
        assertTrue(start.exceptionOrNull()?.message.orEmpty().contains("command, radio, or phone"))
        assertTrue(upload.isFailure)
        assertFalse(upload.exceptionOrNull() is PatrolGridTransientException)
        assertTrue(upload.exceptionOrNull()?.message.orEmpty().contains("GPS evidence limit"))
        assertFalse(upload.exceptionOrNull()?.message.orEmpty().contains("support", ignoreCase = true))
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
