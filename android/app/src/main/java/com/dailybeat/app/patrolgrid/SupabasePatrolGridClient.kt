package com.dailybeat.app.patrolgrid

import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupRemote
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.backup.BackupSessionExpiredException
import com.dailybeat.app.backup.BackupTransientException
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRouteGuidance
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabasePatrolGridClient(
    private val configuration: BackupConfiguration,
    private val sessionRemote: BackupRemote,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val clock: () -> Long = System::currentTimeMillis,
) : PatrolGridRemote {

    override val isConfigured: Boolean get() = configuration.isConfigured

    override fun currentSession(): BackupSession? = sessionRemote.currentSession()

    override suspend fun signIn(email: String, password: String): Result<PatrolGridIdentity> {
        val authentication = sessionRemote.signIn(email, password)
        if (authentication.isFailure) return Result.failure(requireNotNull(authentication.exceptionOrNull()))
        return loadIdentity().also { identity ->
            if (identity.isFailure) sessionRemote.signOut()
        }
    }

    override suspend fun loadIdentity(): Result<PatrolGridIdentity> = ioResult {
        val session = authenticatedSession()
        loadIdentity(session)
    }

    override suspend fun loadSnapshot(activeMissionId: String?): Result<PatrolGridRemoteSnapshot> = ioResult {
        val session = authenticatedSession()
        val identity = loadIdentity(session)
        post(
            path = "/rest/v1/rpc/patrolgrid_close_expired_sessions",
            body = "{}",
            session = session,
            returnRows = true,
        )
        val missionRows = getRows(
            path = "/rest/v1/patrolgrid_missions" +
                "?select=id,title,starts_at,ends_at,guidance,instructions,status,version,route_geojson,updated_at,retention_until" +
                "&order=starts_at.desc&limit=100",
            session = session,
        )
        val missionIds = (0 until missionRows.length()).map { missionRows.getJSONObject(it).getString("id") }
        // An officer whose device lost its local patrol state still has the session running
        // on the server. Without this the app renders the mission "On route" and then tells
        // them it is closed, offering no action at all, so the patrol can be neither recorded
        // into nor ended. Row-level security scopes this to the caller's own sessions.
        // Only worth asking when a resume could actually apply: this device holds no local
        // active patrol, so nothing is being tracked here. When it does hold one there is
        // nothing orphaned and the common refresh path keeps its original request count.
        val openSessionRow = if (identity.role == PatrolRole.PATROL && activeMissionId == null) {
            getRows(
                path = "/rest/v1/patrolgrid_sessions" +
                    "?select=id,mission_id,started_at&ended_at=is.null" +
                    "&order=started_at.desc&limit=1",
                session = session,
            ).takeIf { it.length() > 0 }?.getJSONObject(0)
        } else {
            null
        }
        val resumableMission = openSessionRow?.getString("mission_id")?.takeIf { it in missionIds }
        val resumableSession = openSessionRow?.getString("id")?.takeIf { resumableMission != null }
        val evidenceMissionId = activeMissionId?.takeIf { it in missionIds }
            ?: (0 until missionRows.length()).map { missionRows.getJSONObject(it) }
                .firstOrNull { it.getString("status") == "active" }?.getString("id")
            ?: (0 until missionRows.length()).map { missionRows.getJSONObject(it) }
                .firstOrNull { it.getString("status") in setOf("needs_review", "assigned") }?.getString("id")
            ?: missionIds.firstOrNull()
        val priorities = if (missionIds.isEmpty()) JSONArray() else getAllRows(
            path = "/rest/v1/patrolgrid_priority_locations" +
                "?select=id,mission_id,name,latitude,longitude,radius_m,required,sort_order" +
                "&mission_id=in.(${missionIds.joinToString(",")})&order=sort_order.asc",
            session = session,
        )
        val visits = if (missionIds.isEmpty()) JSONArray() else getAllRows(
            path = "/rest/v1/patrolgrid_priority_visits" +
                "?select=id,session_id,mission_id,priority_location_id,user_id,visited_at,created_at,method,accuracy_m,note" +
                "&mission_id=in.(${missionIds.joinToString(",")})" +
                "&order=visited_at.desc,created_at.desc,id.desc",
            session = session,
        )
        val assignments = if (missionIds.isEmpty()) JSONArray() else getAllRows(
            path = "/rest/v1/patrolgrid_assignments" +
                "?select=mission_id,user_id&mission_id=in.(${missionIds.joinToString(",")})",
            session = session,
        )
        val fieldUpdates = if (missionIds.isEmpty()) JSONArray() else getAllRows(
            path = "/rest/v1/patrolgrid_field_updates" +
                "?select=id,mission_id,category,detail,occurred_at,created_at,review_id" +
                "&mission_id=in.(${missionIds.joinToString(",")})" +
                "&order=occurred_at.desc,created_at.desc,id.desc",
            session = session,
        )
        val latestReview = evidenceMissionId?.let { missionId ->
            getRows(
                path = "/rest/v1/patrolgrid_reviews" +
                    "?select=id,outcome,notes,reviewed_at,created_at&mission_id=eq.$missionId" +
                    "&order=reviewed_at.desc,created_at.desc,id.desc&limit=1",
                session = session,
            )
        } ?: JSONArray()
        val evidenceSources = evidenceMissionId?.let { missionId ->
            val rows = getAllRows(
                path = "/rest/v1/patrolgrid_evidence_session_summaries" +
                    "?select=session_id,mission_id,user_id,display_name,badge_number,started_at,ended_at,end_reason," +
                    "app_version,track_point_count,first_recorded_at,last_recorded_at,first_received_at," +
                    "last_received_at,best_accuracy_m,worst_accuracy_m" +
                    "&mission_id=eq.${encoded(missionId)}" +
                    "&order=started_at.desc,session_id.desc",
                session = session,
                maxRows = MAX_EVIDENCE_SOURCES,
            )
            parseEvidenceSources(rows, missionId)
                .let { sources ->
                    if (identity.role == PatrolRole.PATROL) {
                        sources.filter { it.userId == identity.userId }
                    } else {
                        sources
                    }
                }
        }.orEmpty()
        val selectedEvidenceSource = when (identity.role) {
            PatrolRole.PATROL -> evidenceSources.firstOrNull { it.endedAtMs == null }
                ?: evidenceSources.firstOrNull()
            PatrolRole.SUPERVISOR -> evidenceSources.firstOrNull()
        }
        val evidenceTrail = selectedEvidenceSource?.let { source ->
            loadEvidenceTrail(source.sessionId, session)
        }

        val visitedIds = (0 until visits.length())
            .map { visits.getJSONObject(it).getString("priority_location_id") }
            .toSet()
        val prioritiesByMission = (0 until priorities.length())
            .map { priorities.getJSONObject(it) }
            .groupBy { it.getString("mission_id") }
        val personnelByMission = (0 until assignments.length())
            .map { assignments.getJSONObject(it).getString("mission_id") }
            .groupingBy { it }
            .eachCount()
        val deviationsByMission = (0 until fieldUpdates.length())
            .map { fieldUpdates.getJSONObject(it) }
            .filter { it.getString("category") == "operational_deviation" }
            .map { it.getString("mission_id") }
            .toSet()

        val missions = (0 until missionRows.length()).map { index ->
            val row = missionRows.getJSONObject(index)
            val missionId = row.getString("id")
            val sourceStatus = row.getString("status")
            val retentionUntilEpochMs = parsePatrolMissionRetentionDeadline(
                sourceStatus = sourceStatus,
                rawValue = row.opt("retention_until")
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
                    .orEmpty(),
            )
            val activeLocally = missionId == activeMissionId &&
                identity.role == PatrolRole.PATROL &&
                sourceStatus !in PATROLGRID_TERMINAL_SOURCE_STATUSES
            val locations = prioritiesByMission[missionId].orEmpty().mapIndexed { locationIndex, location ->
                val visited = location.getString("id") in visitedIds
                val priorVisited = prioritiesByMission[missionId].orEmpty()
                    .take(locationIndex)
                    .all { it.getString("id") in visitedIds }
                val state = when {
                    visited -> PriorityLocationState.VISITED
                    priorVisited && activeLocally -> PriorityLocationState.CURRENT
                    else -> PriorityLocationState.REMAINING
                }
                PriorityLocation(
                    id = location.getString("id"),
                    name = location.getString("name"),
                    state = state,
                    detail = when (state) {
                        PriorityLocationState.VISITED -> "Visited"
                        PriorityLocationState.CURRENT -> "Current"
                        PriorityLocationState.REMAINING -> "Remaining"
                    },
                    required = location.optBoolean("required", true),
                    latitude = location.optDouble("latitude").takeUnless(Double::isNaN),
                    longitude = location.optDouble("longitude").takeUnless(Double::isNaN),
                    radiusM = location.optDouble("radius_m").takeUnless(Double::isNaN),
                )
            }
            val status = if (activeLocally) PatrolMissionStatus.ACTIVE else sourceStatus.toMissionStatus()
            PatrolMission(
                id = missionId,
                title = row.getString("title"),
                dutyWindow = dutyWindow(row.getString("starts_at"), row.getString("ends_at")),
                unitName = if (identity.role == PatrolRole.SUPERVISOR) "Assigned personnel" else identity.displayName,
                personnelCount = personnelByMission[missionId] ?: 0,
                status = status,
                statusLabel = status.label,
                context = row.optString("instructions").ifBlank {
                    if (activeLocally) "Patrol is active on this device" else "Briefing ready"
                },
                priorityLocations = locations,
                lastUpdateLabel = relativeUpdate(row.getString("updated_at")),
                hasOperationalDeviation = missionId in deviationsByMission,
                version = row.optInt("version", 1),
                endsAtEpochMs = runCatching {
                    Instant.parse(row.getString("ends_at")).toEpochMilli()
                }.getOrNull(),
                retentionUntilEpochMs = retentionUntilEpochMs,
            )
        }
        val evidenceRow = (0 until missionRows.length()).map { missionRows.getJSONObject(it) }
            .firstOrNull { it.getString("id") == evidenceMissionId }
        val reviewRow = latestReview.optJSONObject(0)
        val contextResponse = (0 until fieldUpdates.length())
            .map { fieldUpdates.getJSONObject(it) }
            .firstOrNull {
                it.getString("mission_id") == evidenceMissionId &&
                    it.getString("category") == "review_context" &&
                    it.optString("review_id") == reviewRow?.optString("id")
            }
        PatrolGridRemoteSnapshot(
            identity = identity,
            missions = missions.sortedWith(compareByDescending<PatrolMission> { it.id == evidenceMissionId }),
            evidenceMissionId = evidenceMissionId,
            recordedTrackPoints = evidenceMissionId?.let {
                countRows("patrolgrid_track_points", "mission_id=eq.$it", session)
            } ?: 0,
            observationCount = evidenceMissionId?.let {
                countRows("patrolgrid_field_updates", "mission_id=eq.$it&category=eq.observation", session)
            } ?: 0,
            routePoints = evidenceTrail?.routePoints.orEmpty(),
            plannedRoutePoints = PatrolRouteGeoJsonParser.parse(
                evidenceRow?.optJSONObject("route_geojson"),
            ),
            reviewContextRequestId = reviewRow
                ?.takeIf { it.getString("outcome") == "needs_context" }
                ?.optString("id")
                ?.takeIf(String::isNotBlank),
            reviewContextRequest = reviewRow
                ?.takeIf { it.getString("outcome") == "needs_context" }
                ?.optString("notes")
                ?.takeIf(String::isNotBlank),
            reviewContextResponse = contextResponse?.optString("detail")?.takeIf(String::isNotBlank),
            evidenceSources = evidenceSources,
            selectedEvidenceSessionId = selectedEvidenceSource?.sessionId,
            resumableSessionId = resumableSession,
            resumableMissionId = resumableMission,
            priorityVisitEvidence = mapPriorityVisitEvidence(
                visits = visits,
                evidenceMissionId = evidenceMissionId,
                prioritiesByMission = prioritiesByMission,
                evidenceSources = evidenceSources,
                identity = identity,
            ),
        )
    }

    override suspend fun loadEvidenceTrail(sessionId: String): Result<PatrolEvidenceTrail> = ioResult {
        require(sessionId.isNotBlank() && sessionId.length <= 128) {
            "Choose a valid PatrolGrid evidence source."
        }
        loadEvidenceTrail(sessionId, authenticatedSession())
    }

    private fun loadEvidenceTrail(
        sessionId: String,
        session: BackupSession,
    ): PatrolEvidenceTrail {
        require(sessionId.isNotBlank() && sessionId.length <= 128) {
            "Choose a valid PatrolGrid evidence source."
        }
        val rows = getRows(
            path = "/rest/v1/patrolgrid_track_points" +
                "?select=session_id,latitude,longitude&session_id=eq.${encoded(sessionId)}" +
                "&order=recorded_at.desc,sequence_number.desc&limit=1000",
            session = session,
        )
        // PostgREST returns the newest bounded window. Validate its provenance before
        // reversing it into chronological drawing order; never combine session rows.
        val routePoints = (rows.length() - 1 downTo 0).map { index ->
            val point = rows.getJSONObject(index)
            check(point.getString("session_id") == sessionId) {
                "PatrolGrid returned a route point from a different evidence source."
            }
            val latitude = point.requiredFiniteNumber("latitude")
            val longitude = point.requiredFiniteNumber("longitude")
            check(latitude in -90.0..90.0 && longitude in -180.0..180.0) {
                "PatrolGrid returned an invalid recorded location."
            }
            PatrolMapPoint(latitude = latitude, longitude = longitude)
        }
        return PatrolEvidenceTrail(sessionId = sessionId, routePoints = routePoints)
    }

    private fun parseEvidenceSources(
        rows: JSONArray,
        evidenceMissionId: String,
    ): List<PatrolEvidenceSource> = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        check(row.getString("mission_id") == evidenceMissionId) {
            "PatrolGrid returned an evidence source from a different mission."
        }
        val startedAtMs = row.requiredInstantMs("started_at")
        val endedAtMs = row.optionalInstantMs("ended_at")
        check(endedAtMs == null || endedAtMs >= startedAtMs) {
            "PatrolGrid returned an invalid evidence session clock."
        }
        val trackPointCount = row.getInt("track_point_count")
        check(trackPointCount >= 0) { "PatrolGrid returned an invalid route point count." }
        val firstRecordedAtMs = row.optionalInstantMs("first_recorded_at")
        val lastRecordedAtMs = row.optionalInstantMs("last_recorded_at")
        val firstReceivedAtMs = row.optionalInstantMs("first_received_at")
        val lastReceivedAtMs = row.optionalInstantMs("last_received_at")
        val bestAccuracyM = row.optionalBoundedFloat("best_accuracy_m", 0f..5_000f)
        val worstAccuracyM = row.optionalBoundedFloat("worst_accuracy_m", 0f..5_000f)
        val pointMetadata = listOf(
            firstRecordedAtMs,
            lastRecordedAtMs,
            firstReceivedAtMs,
            lastReceivedAtMs,
            bestAccuracyM,
            worstAccuracyM,
        )
        check(
            if (trackPointCount == 0) {
                pointMetadata.all { it == null }
            } else {
                pointMetadata.all { it != null }
            },
        ) {
            "PatrolGrid returned inconsistent evidence summary metadata."
        }
        check(
            firstRecordedAtMs == null ||
                lastRecordedAtMs == null ||
                firstRecordedAtMs <= lastRecordedAtMs,
        ) { "PatrolGrid returned an invalid recorded evidence clock." }
        check(
            firstReceivedAtMs == null ||
                lastReceivedAtMs == null ||
                firstReceivedAtMs <= lastReceivedAtMs,
        ) { "PatrolGrid returned an invalid received evidence clock." }
        check(bestAccuracyM == null || worstAccuracyM == null || bestAccuracyM <= worstAccuracyM) {
            "PatrolGrid returned an invalid evidence accuracy range."
        }
        PatrolEvidenceSource(
            sessionId = row.getString("session_id"),
            userId = row.getString("user_id"),
            displayName = row.getString("display_name"),
            badgeNumber = row.optionalString("badge_number")?.takeIf(String::isNotBlank),
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            endReason = row.optionalString("end_reason"),
            appVersion = row.getString("app_version").also {
                check(it.isNotBlank()) { "PatrolGrid returned an invalid app version." }
            },
            trackPointCount = trackPointCount,
            firstRecordedAtMs = firstRecordedAtMs,
            lastRecordedAtMs = lastRecordedAtMs,
            firstReceivedAtMs = firstReceivedAtMs,
            lastReceivedAtMs = lastReceivedAtMs,
            bestAccuracyM = bestAccuracyM,
            worstAccuracyM = worstAccuracyM,
        )
    }.sortedWith(compareByDescending<PatrolEvidenceSource> { it.startedAtMs }.thenByDescending { it.sessionId })

    private fun mapPriorityVisitEvidence(
        visits: JSONArray,
        evidenceMissionId: String?,
        prioritiesByMission: Map<String, List<JSONObject>>,
        evidenceSources: List<PatrolEvidenceSource>,
        identity: PatrolGridIdentity,
    ): List<PatrolPriorityVisitEvidence> {
        evidenceMissionId ?: return emptyList()
        val priorityNames = prioritiesByMission[evidenceMissionId].orEmpty()
            .associate { it.getString("id") to it.getString("name") }
        val sourcesBySessionId = evidenceSources.associateBy(PatrolEvidenceSource::sessionId)
        return (0 until visits.length()).map { visits.getJSONObject(it) }
            .filter { it.getString("mission_id") == evidenceMissionId }
            .map { visit ->
                val sessionId = visit.getString("session_id")
                val source = checkNotNull(sourcesBySessionId[sessionId]) {
                    "PatrolGrid returned priority evidence without its session source."
                }
                val priorityLocationId = visit.getString("priority_location_id")
                val userId = visit.getString("user_id")
                check(userId == source.userId) {
                    "PatrolGrid returned priority evidence from a different person."
                }
                val method = visit.getString("method")
                check(method in setOf("gps", "manual_with_context")) {
                    "PatrolGrid returned an invalid priority visit method."
                }
                PatrolPriorityVisitEvidence(
                    sessionId = sessionId,
                    priorityLocationId = priorityLocationId,
                    priorityName = checkNotNull(priorityNames[priorityLocationId]) {
                        "PatrolGrid returned priority evidence without its location."
                    },
                    userId = userId,
                    displayName = source.displayName.ifBlank {
                        identity.takeIf { it.userId == userId }?.displayName ?: "Patrol personnel"
                    },
                    visitedAtMs = visit.requiredInstantMs("visited_at"),
                    receivedAtMs = visit.requiredInstantMs("created_at"),
                    method = method,
                    accuracyM = visit.optionalBoundedFloat("accuracy_m", 0f..5_000f),
                    note = visit.optionalString("note")?.takeIf(String::isNotBlank),
                )
            }
    }

    override suspend fun startSession(
        missionId: String,
        installationId: String,
        appVersion: String,
    ): Result<String> = ioResult {
        val session = authenticatedSession()
        val patrolSessionId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("target_session", patrolSessionId)
            .put("target_mission", missionId)
            .put("target_installation", installationId)
            .put("target_app_version", appVersion)
        val response = post(
            "/rest/v1/rpc/patrolgrid_start_session",
            payload.toString(),
            session,
            returnRows = true,
        )
        response.trim().trim('[', ']', '"').takeIf(String::isNotBlank)
            ?: error("PatrolGrid did not return the active session id.")
    }

    override suspend fun loadAssignmentOptions(): Result<PatrolAssignmentOptions> = ioResult {
        val session = authenticatedSession()
        val routes = getRows(
            "/rest/v1/patrolgrid_route_templates" +
                "?select=id,name,default_start_time,default_duration_minutes&is_active=eq.true&order=name.asc",
            session,
        )
        val routeIds = (0 until routes.length()).map { routes.getJSONObject(it).getString("id") }
        val priorities = if (routeIds.isEmpty()) JSONArray() else getRows(
            "/rest/v1/patrolgrid_route_template_priorities" +
                "?select=route_template_id,name,sort_order" +
                "&route_template_id=in.(${routeIds.joinToString(",")})&order=sort_order.asc",
            session,
        )
        val prioritiesByRoute = (0 until priorities.length())
            .map { priorities.getJSONObject(it) }
            .groupBy { it.getString("route_template_id") }
        val routeOptions = (0 until routes.length()).map { index ->
            val route = routes.getJSONObject(index)
            val start = java.time.LocalTime.parse(route.getString("default_start_time"))
            val end = start.plusMinutes(route.getLong("default_duration_minutes"))
            PatrolRoutePlan(
                id = route.getString("id"),
                title = route.getString("name"),
                dutyWindow = "${TIME.format(start)}–${TIME.format(end)}",
                priorityLocations = prioritiesByRoute[route.getString("id")].orEmpty()
                    .map { it.getString("name") },
            )
        }

        val units = getRows(
            "/rest/v1/patrolgrid_units?select=id,name&is_active=eq.true&order=name.asc",
            session,
        )
        val unitIds = (0 until units.length()).map { units.getJSONObject(it).getString("id") }
        val unitMembers = if (unitIds.isEmpty()) JSONArray() else getRows(
            "/rest/v1/patrolgrid_unit_members?select=unit_id,user_id" +
                "&unit_id=in.(${unitIds.joinToString(",")})",
            session,
        )
        val countByUnit = (0 until unitMembers.length())
            .map { unitMembers.getJSONObject(it).getString("unit_id") }
            .groupingBy { it }
            .eachCount()
        val unitOptions = (0 until units.length()).map { index ->
            val unit = units.getJSONObject(index)
            PatrolUnitOption(
                name = unit.getString("name"),
                personnelCount = countByUnit[unit.getString("id")] ?: 0,
                id = unit.getString("id"),
            )
        }.filter { it.personnelCount > 0 }
        PatrolAssignmentOptions(routeOptions, unitOptions)
    }

    override suspend fun createAssignment(draft: PatrolAssignmentDraft): Result<Unit> = ioResult {
        val session = authenticatedSession()
        val unitId = requireNotNull(draft.unitId) { "Choose a server-managed patrol unit." }
        val guidance = when (draft.guidance) {
            PatrolRouteGuidance.SUGGESTED_ROUTE -> "suggested_route"
            PatrolRouteGuidance.AREA_COVERAGE -> "area_coverage"
        }
        val payload = JSONObject()
            .put("target_route_template", draft.routePlanId)
            .put("target_unit", unitId)
            .put("target_guidance", guidance)
        post("/rest/v1/rpc/patrolgrid_create_assignment", payload.toString(), session)
        Unit
    }

    override suspend fun submitReview(
        missionId: String,
        expectedVersion: Int,
        outcome: SupervisorReviewOutcome,
        notes: String,
    ): Result<Int> = ioResult {
        require(notes.length <= 4_000) { "Review notes are too long." }
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("target_mission", missionId)
            .put("target_expected_version", expectedVersion)
            .put("target_outcome", outcome.storageValue)
            .put("target_notes", notes.trim())
        val response = post(
            "/rest/v1/rpc/patrolgrid_submit_review",
            payload.toString(),
            session,
            returnRows = true,
        )
        response.trim().trim('[', ']', '"').toIntOrNull()
            ?: error("PatrolGrid did not return the reviewed mission version.")
    }

    override suspend fun endSession(
        sessionId: String,
        reason: String,
        endedAtMs: Long,
    ): Result<Unit> = ioResult {
        require(
            reason in setOf(
                "completed",
                "relieved",
                "cancelled",
                "device_issue",
                "duty_window_ended",
            ),
        )
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("target_session", sessionId)
            .put("target_reason", reason)
        post(
            "/rest/v1/rpc/patrolgrid_end_session",
            payload.toString(),
            session,
            returnRows = true,
            evidenceWrite = true,
        )
        Unit
    }

    override suspend fun uploadTrackPoints(
        missionId: String,
        sessionId: String,
        points: List<RemoteTrackPoint>,
    ): Result<Unit> = ioResult {
        if (points.isEmpty()) return@ioResult Unit
        require(points.size <= 250) { "A route upload cannot exceed 250 points." }
        val session = authenticatedSession()
        val payload = JSONArray()
        points.forEach { point ->
            require(point.sequenceNumber >= 0)
            require(point.latitude.isFinite() && point.latitude in -90.0..90.0)
            require(point.longitude.isFinite() && point.longitude in -180.0..180.0)
            require(point.accuracyM.isFinite() && point.accuracyM in 0f..5_000f)
            payload.put(
                JSONObject()
                    .put("client_point_id", point.clientPointId)
                    .put("sequence_number", point.sequenceNumber)
                    .put("recorded_at", Instant.ofEpochMilli(point.recordedAtMs).toString())
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .put("accuracy_m", point.accuracyM),
            )
        }
        val rpcPayload = JSONObject()
            .put("target_session", sessionId)
            .put("target_points", payload)
            .toString()
        require(rpcPayload.toByteArray(StandardCharsets.UTF_8).size <= 262_144) {
            "The route upload payload is too large."
        }
        post(
            "/rest/v1/rpc/patrolgrid_ingest_track_points",
            rpcPayload,
            session,
            returnRows = true,
            evidenceWrite = true,
        )
        Unit
    }

    override suspend fun markPriorityVisited(
        sessionId: String,
        priorityLocationId: String,
        clientVisitId: String,
        visitedAtMs: Long,
    ): Result<Unit> = ioResult {
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("target_session", sessionId)
            .put("target_visit", clientVisitId)
            .put("target_priority_location", priorityLocationId)
            .put("target_visited_at", Instant.ofEpochMilli(visitedAtMs).toString())
            .put("target_method", "manual_with_context")
            .put("target_note", "Marked visited by patrol personnel")
        post(
            "/rest/v1/rpc/patrolgrid_record_priority_visit",
            payload.toString(),
            session,
            returnRows = true,
            evidenceWrite = true,
        )
        Unit
    }

    override suspend fun addFieldUpdate(
        sessionId: String?,
        category: String,
        detail: String,
        clientUpdateId: String,
        occurredAtMs: Long,
        reviewId: String?,
    ): Result<Unit> = ioResult {
        require(category in setOf("observation", "operational_deviation", "safety_event", "review_context"))
        require(detail.isNotBlank())
        require(detail.length <= 4_000 && detail.toByteArray(StandardCharsets.UTF_8).size <= 16_000)
        require((category == "review_context") == !reviewId.isNullOrBlank())
        require((category == "review_context") == sessionId.isNullOrBlank())
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("target_client_update", clientUpdateId)
            .put("target_category", category)
            .put("target_detail", detail)
            .put("target_occurred_at", Instant.ofEpochMilli(occurredAtMs).toString())
        if (sessionId != null) payload.put("target_session", sessionId)
        if (reviewId != null) payload.put("target_review", reviewId)
        post(
            "/rest/v1/rpc/patrolgrid_record_field_update",
            payload.toString(),
            session,
            returnRows = true,
            evidenceWrite = true,
        )
        Unit
    }

    override fun signOut() = sessionRemote.signOut()

    override suspend fun revokeSession(): Result<Unit> = sessionRemote.revokeSession()

    private suspend fun authenticatedSession(): BackupSession = sessionRemote.authenticatedSession().fold(
        onSuccess = { it },
        onFailure = { error ->
            when {
                error is BackupSessionExpiredException || sessionRemote.currentSession() == null ->
                    throw PatrolGridSessionExpiredException()
                error is BackupTransientException ->
                    throw PatrolGridTransientException(
                        "PatrolGrid could not renew the secure session while offline. Try again when connected.",
                    )
                else -> throw error
            }
        },
    )

    private fun loadIdentity(session: BackupSession): PatrolGridIdentity {
        val memberships = getRows(
            "/rest/v1/patrolgrid_memberships" +
                "?select=subdivision_id,role,display_name,badge_number" +
                "&user_id=eq.${encoded(session.userId)}&status=eq.active&limit=1",
            session,
        )
        if (memberships.length() != 1) {
            throw PatrolGridAccessDeniedException(
                "Your PatrolGrid account is not assigned to an active subdivision. Contact your subdivision supervisor through the existing official Department channel.",
            )
        }
        val membership = memberships.getJSONObject(0)
        val subdivisionId = membership.getString("subdivision_id")
        val subdivisions = getRows(
            "/rest/v1/patrolgrid_subdivisions?select=name&id=eq.${encoded(subdivisionId)}&limit=1",
            session,
        )
        if (subdivisions.length() != 1) {
            throw PatrolGridAccessDeniedException("Your assigned subdivision is unavailable.")
        }
        return PatrolGridIdentity(
            userId = session.userId,
            subdivisionId = subdivisionId,
            subdivisionName = subdivisions.getJSONObject(0).getString("name"),
            displayName = membership.getString("display_name"),
            badgeNumber = membership.optString("badge_number").takeIf(String::isNotBlank),
            role = PatrolRole.fromStorage(membership.getString("role")),
        )
    }

    private fun getRows(path: String, session: BackupSession): JSONArray {
        val request = authorizedRequest(path, session).get().build()
        return JSONArray(execute(request))
    }

    /**
     * PostgREST deployments commonly cap one response at 1,000 rows. Evidence must
     * never look complete merely because that server cap silently truncated it.
     */
    private fun getAllRows(
        path: String,
        session: BackupSession,
        maxRows: Int = MAX_SNAPSHOT_ROWS,
    ): JSONArray {
        require("limit=" !in path && "offset=" !in path)
        require(maxRows >= PAGE_SIZE && maxRows % PAGE_SIZE == 0)
        val combined = JSONArray()
        var offset = 0
        while (true) {
            val separator = if ('?' in path) '&' else '?'
            val page = getRows(
                "$path${separator}limit=$PAGE_SIZE&offset=$offset",
                session,
            )
            for (index in 0 until page.length()) combined.put(page.get(index))
            if (page.length() < PAGE_SIZE) return combined
            offset += page.length()
            check(offset < maxRows) {
                "PatrolGrid evidence exceeds the safe mobile review limit. " +
                    "Use the official supervisor channel to narrow the mission review."
            }
        }
    }

    private fun countRows(table: String, filters: String, session: BackupSession): Int {
        val request = authorizedRequest("/rest/v1/$table?select=id&$filters&limit=1", session)
            .header("Prefer", "count=exact")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            ensureSuccess(response.code)
            return response.header("Content-Range")
                ?.substringAfter('/')
                ?.toIntOrNull()
                ?: 0
        }
    }

    private fun post(
        path: String,
        body: String,
        session: BackupSession,
        returnRows: Boolean = false,
        evidenceWrite: Boolean = false,
    ): String {
        val preferences = if (returnRows) "return=representation" else "return=minimal"
        val request = authorizedRequest(path, session)
            .header("Prefer", preferences)
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request, evidenceWrite)
    }

    private fun authorizedRequest(path: String, session: BackupSession): Request.Builder = Request.Builder()
        .url(configuration.baseUrl + path)
        .header("apikey", configuration.anonymousKey)
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("Content-Type", "application/json")

    private fun execute(request: Request, evidenceWrite: Boolean = false): String {
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            ensureSuccess(response.code, evidenceWrite, body)
            return body
        }
    }

    private fun ensureSuccess(code: Int, evidenceWrite: Boolean = false, responseBody: String = "") {
        if (code in 200..299) return
        val postgresErrorCode = runCatching {
            JSONObject(responseBody).optString("code").takeIf(String::isNotBlank)
        }.getOrNull()
        val postgresMessage = runCatching {
            JSONObject(responseBody).optString("message").takeIf(String::isNotBlank)
        }.getOrNull()
        if (evidenceWrite && postgresErrorCode == "P0002") {
            throw PatrolGridEvidenceUnavailableException()
        }
        if (postgresErrorCode == "54000") {
            when (postgresMessage) {
                "Patrol assignment session limit exceeded" -> throw PatrolGridRemoteException(
                    "This assignment reached its secure session limit. Do not keep retrying; " +
                        "contact your supervisor through the normal command, radio, or phone chain.",
                )
                "Patrol session restart rate limit exceeded" -> throw PatrolGridRemoteException(
                    "Too many patrol session restarts were requested. Wait 15 minutes; if patrol " +
                        "must continue, contact your supervisor through the normal command, radio, or phone chain.",
                )
                "Track assignment point limit exceeded" -> throw PatrolGridRemoteException(
                    "This assignment reached its secure GPS evidence limit. Do not treat the route " +
                        "as synchronized; report the device issue through the normal command, radio, or phone chain.",
                )
            }
        }
        val error = when (code) {
            400 -> PatrolGridRemoteException("PatrolGrid rejected invalid mission data. Refresh and try again.")
            401 -> PatrolGridSessionExpiredException()
            403 -> PatrolGridAccessDeniedException()
            404 -> PatrolGridRemoteException("The requested patrol record is no longer available.")
            409 -> PatrolGridRemoteException(
                "This patrol record changed on another device. Refresh before continuing.",
            )
            429 -> PatrolGridTransientException(
                "PatrolGrid is busy. Your local evidence is safe; retry shortly.",
            )
            in 500..599 -> PatrolGridTransientException("PatrolGrid service is temporarily unavailable.")
            else -> PatrolGridRemoteException("PatrolGrid request failed ($code).")
        }
        if (code == 401) sessionRemote.signOut()
        throw error
    }

    private suspend fun <T> ioResult(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
        try {
            Result.success(block())
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun dutyWindow(start: String, end: String): String {
        val zone = ZoneId.systemDefault()
        val startTime = Instant.parse(start).atZone(zone)
        val endTime = Instant.parse(end).atZone(zone)
        return "${DATE_TIME.format(startTime)}–${TIME.format(endTime)}"
    }

    private fun relativeUpdate(updatedAt: String): String {
        val minutes = ((clock() - Instant.parse(updatedAt).toEpochMilli()) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> "Now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1_440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1_440}d ago"
        }
    }

    private fun JSONObject.requiredFiniteNumber(name: String): Double {
        val raw = opt(name)
        check(raw is Number) { "PatrolGrid returned an invalid numeric value." }
        return raw.toDouble().also { value ->
            check(value.isFinite()) { "PatrolGrid returned an invalid numeric value." }
        }
    }

    private fun JSONObject.requiredInstantMs(name: String): Long {
        val raw = opt(name)
        check(raw is String && raw.isNotBlank()) {
            "PatrolGrid returned an invalid evidence clock."
        }
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse {
            throw IllegalStateException("PatrolGrid returned an invalid evidence clock.")
        }
    }

    private fun JSONObject.optionalInstantMs(name: String): Long? =
        optionalString(name)?.let { raw ->
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrElse {
                throw IllegalStateException("PatrolGrid returned an invalid evidence clock.")
            }
        }

    private fun JSONObject.optionalString(name: String): String? {
        val raw = opt(name) ?: return null
        if (raw == JSONObject.NULL) return null
        check(raw is String) { "PatrolGrid returned an invalid text value." }
        return raw
    }

    private fun JSONObject.optionalBoundedFloat(
        name: String,
        range: ClosedFloatingPointRange<Float>,
    ): Float? {
        val raw = opt(name) ?: return null
        if (raw == JSONObject.NULL) return null
        check(raw is Number) { "PatrolGrid returned an invalid accuracy value." }
        val value = raw.toDouble()
        check(value.isFinite() && value >= range.start && value <= range.endInclusive) {
            "PatrolGrid returned an invalid accuracy value."
        }
        return value.toFloat()
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_SNAPSHOT_ROWS = 5_000
        const val MAX_EVIDENCE_SOURCES = 1_000
        val JSON = "application/json; charset=utf-8".toMediaType()
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM · HH:mm")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun encoded(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        fun String.toMissionStatus(): PatrolMissionStatus = when (this) {
            "active" -> PatrolMissionStatus.ACTIVE
            "completed" -> PatrolMissionStatus.COMPLETED
            "needs_review", "cancelled" -> PatrolMissionStatus.NEEDS_REVIEW
            else -> PatrolMissionStatus.ASSIGNED
        }

        val PatrolMissionStatus.label: String
            get() = when (this) {
                PatrolMissionStatus.ACTIVE -> "On route"
                PatrolMissionStatus.PAUSED_WITH_REASON -> "Paused with reason"
                PatrolMissionStatus.COMPLETED -> "Ready for review"
                PatrolMissionStatus.NEEDS_REVIEW -> "Needs context"
                PatrolMissionStatus.ASSIGNED -> "Assigned"
            }
    }
}

private val PATROLGRID_SOURCE_MISSION_STATUSES = setOf(
    "planned",
    "assigned",
    "active",
    "needs_review",
    "completed",
    "cancelled",
)

internal val PATROLGRID_TERMINAL_SOURCE_STATUSES = setOf(
    "needs_review",
    "completed",
    "cancelled",
)

internal fun parsePatrolMissionRetentionDeadline(sourceStatus: String, rawValue: String): Long? {
    check(sourceStatus in PATROLGRID_SOURCE_MISSION_STATUSES) {
        "PatrolGrid returned an unsupported mission state."
    }
    val deadline = rawValue.trim().takeIf(String::isNotEmpty)?.let { value ->
        runCatching { Instant.parse(value).toEpochMilli() }.getOrElse {
            throw IllegalStateException("PatrolGrid returned an invalid mission retention clock.")
        }
    }
    check((deadline != null) == (sourceStatus in PATROLGRID_TERMINAL_SOURCE_STATUSES)) {
        "PatrolGrid returned an inconsistent mission retention clock."
    }
    return deadline
}

internal object PatrolRouteGeoJsonParser {
    private const val MAX_BYTES = 262_144
    private const val MAX_POINTS = 10_000
    private const val MAX_ARRAYS = 12_000

    fun parse(value: JSONObject?): List<PatrolMapPoint> {
        value ?: return emptyList()
        if (value.toString().toByteArray(StandardCharsets.UTF_8).size > MAX_BYTES) return emptyList()
        val coordinates = value.optJSONArray("coordinates") ?: return emptyList()
        val positionContainerDepth = when (value.optString("type")) {
            "LineString" -> 0
            "MultiLineString", "Polygon" -> 1
            "MultiPolygon" -> 2
            else -> return emptyList()
        }

        data class PendingArray(val value: JSONArray, val depth: Int)

        val pending = ArrayDeque<PendingArray>().apply {
            addLast(PendingArray(coordinates, 0))
        }
        val points = ArrayList<PatrolMapPoint>()
        var arraysVisited = 0
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            arraysVisited += 1
            if (arraysVisited > MAX_ARRAYS || current.depth > positionContainerDepth) return emptyList()
            if (current.depth == positionContainerDepth) {
                for (index in 0 until current.value.length()) {
                    val position = current.value.optJSONArray(index) ?: return emptyList()
                    if (position.length() != 2 || position.opt(0) !is Number || position.opt(1) !is Number) {
                        return emptyList()
                    }
                    val longitude = position.getDouble(0)
                    val latitude = position.getDouble(1)
                    if (!longitude.isFinite() || longitude !in -180.0..180.0 ||
                        !latitude.isFinite() || latitude !in -90.0..90.0
                    ) {
                        return emptyList()
                    }
                    points += PatrolMapPoint(latitude = latitude, longitude = longitude)
                    if (points.size > MAX_POINTS) return emptyList()
                }
            } else {
                for (index in 0 until current.value.length()) {
                    val child = current.value.optJSONArray(index) ?: return emptyList()
                    pending.addLast(PendingArray(child, current.depth + 1))
                }
            }
        }
        return points
    }
}
