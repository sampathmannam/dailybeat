package com.dailybeat.app.patrolgrid

import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupRemote
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRouteGuidance
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
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
        val missionRows = getRows(
            path = "/rest/v1/patrolgrid_missions" +
                "?select=id,title,starts_at,ends_at,guidance,instructions,status,updated_at" +
                "&order=starts_at.desc&limit=100",
            session = session,
        )
        val missionIds = (0 until missionRows.length()).map { missionRows.getJSONObject(it).getString("id") }
        val routeMissionId = activeMissionId?.takeIf { it in missionIds } ?: missionIds.firstOrNull()
        val priorities = if (missionIds.isEmpty()) JSONArray() else getRows(
            path = "/rest/v1/patrolgrid_priority_locations" +
                "?select=id,mission_id,name,required,sort_order" +
                "&mission_id=in.(${missionIds.joinToString(",")})&order=sort_order.asc",
            session = session,
        )
        val visits = if (missionIds.isEmpty()) JSONArray() else getRows(
            path = "/rest/v1/patrolgrid_priority_visits" +
                "?select=mission_id,priority_location_id,visited_at" +
                "&mission_id=in.(${missionIds.joinToString(",")})",
            session = session,
        )
        val assignments = if (missionIds.isEmpty()) JSONArray() else getRows(
            path = "/rest/v1/patrolgrid_assignments" +
                "?select=mission_id,user_id&mission_id=in.(${missionIds.joinToString(",")})",
            session = session,
        )
        val routePointRows = routeMissionId?.let { missionId ->
            getRows(
                "/rest/v1/patrolgrid_track_points" +
                    "?select=latitude,longitude&mission_id=eq.$missionId" +
                    "&order=recorded_at.desc&limit=1000",
                session,
            )
        } ?: JSONArray()

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

        val missions = (0 until missionRows.length()).map { index ->
            val row = missionRows.getJSONObject(index)
            val missionId = row.getString("id")
            val activeLocally = missionId == activeMissionId
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
                )
            }
            val sourceStatus = row.getString("status")
            val status = if (activeLocally) PatrolMissionStatus.ACTIVE else sourceStatus.toMissionStatus()
            PatrolMission(
                id = missionId,
                title = row.getString("title"),
                dutyWindow = dutyWindow(row.getString("starts_at"), row.getString("ends_at")),
                unitName = if (identity.role == PatrolRole.SUPERVISOR) "Assigned personnel" else identity.displayName,
                personnelCount = personnelByMission[missionId] ?: 1,
                status = status,
                statusLabel = status.label,
                context = row.optString("instructions").ifBlank {
                    if (activeLocally) "Patrol is active on this device" else "Briefing ready"
                },
                priorityLocations = locations,
                lastUpdateLabel = relativeUpdate(row.getString("updated_at")),
                hasOperationalDeviation = sourceStatus == "needs_review",
            )
        }
        val primaryId = activeMissionId ?: missions.firstOrNull {
            it.status == PatrolMissionStatus.ACTIVE || it.status == PatrolMissionStatus.ASSIGNED
        }?.id
        PatrolGridRemoteSnapshot(
            identity = identity,
            missions = missions.sortedWith(compareByDescending<PatrolMission> { it.id == primaryId }),
            recordedTrackPoints = primaryId?.let {
                countRows("patrolgrid_track_points", "mission_id=eq.$it", session)
            } ?: 0,
            observationCount = primaryId?.let {
                countRows("patrolgrid_field_updates", "mission_id=eq.$it&category=eq.observation", session)
            } ?: 0,
            // The API selects the most recent bounded window; reverse it for chronological drawing.
            routePoints = (routePointRows.length() - 1 downTo 0).map { index ->
                val point = routePointRows.getJSONObject(index)
                PatrolMapPoint(point.getDouble("latitude"), point.getDouble("longitude"))
            },
        )
    }

    override suspend fun startSession(
        missionId: String,
        installationId: String,
        appVersion: String,
    ): Result<String> = ioResult {
        val session = authenticatedSession()
        val patrolSessionId = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("id", patrolSessionId)
            .put("mission_id", missionId)
            .put("user_id", session.userId)
            .put("installation_id", installationId)
            .put("started_at", Instant.ofEpochMilli(clock()).toString())
            .put("app_version", appVersion)
        post("/rest/v1/patrolgrid_sessions", payload.toString(), session, returnRows = true)
        patrolSessionId
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

    override suspend fun endSession(sessionId: String, reason: String): Result<Unit> = ioResult {
        require(reason in setOf("completed", "relieved", "cancelled", "device_issue"))
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("ended_at", Instant.ofEpochMilli(clock()).toString())
            .put("end_reason", reason)
        patch(
            "/rest/v1/patrolgrid_sessions?id=eq.${encoded(sessionId)}",
            payload.toString(),
            session,
        )
        Unit
    }

    override suspend fun uploadTrackPoints(
        missionId: String,
        sessionId: String,
        points: List<RemoteTrackPoint>,
    ): Result<Unit> = ioResult {
        if (points.isEmpty()) return@ioResult Unit
        val session = authenticatedSession()
        val payload = JSONArray()
        points.forEach { point ->
            payload.put(
                JSONObject()
                    .put("client_point_id", point.clientPointId)
                    .put("session_id", sessionId)
                    .put("mission_id", missionId)
                    .put("user_id", session.userId)
                    .put("sequence_number", point.sequenceNumber)
                    .put("recorded_at", Instant.ofEpochMilli(point.recordedAtMs).toString())
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .put("accuracy_m", point.accuracyM),
            )
        }
        post(
            "/rest/v1/patrolgrid_track_points?on_conflict=user_id,client_point_id",
            payload.toString(),
            session,
            ignoreDuplicates = true,
        )
        Unit
    }

    override suspend fun markPriorityVisited(
        missionId: String,
        priorityLocationId: String,
        clientVisitId: String,
        visitedAtMs: Long,
    ): Result<Unit> = ioResult {
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("id", clientVisitId)
            .put("priority_location_id", priorityLocationId)
            .put("mission_id", missionId)
            .put("user_id", session.userId)
            .put("visited_at", Instant.ofEpochMilli(visitedAtMs).toString())
            .put("method", "manual_with_context")
            .put("note", "Marked visited by patrol personnel")
        post(
            "/rest/v1/patrolgrid_priority_visits?on_conflict=priority_location_id,user_id",
            payload.toString(),
            session,
            ignoreDuplicates = true,
        )
        Unit
    }

    override suspend fun addFieldUpdate(
        missionId: String,
        category: String,
        detail: String,
        clientUpdateId: String,
        occurredAtMs: Long,
    ): Result<Unit> = ioResult {
        require(category in setOf("observation", "operational_deviation", "safety_event"))
        require(detail.isNotBlank())
        val session = authenticatedSession()
        val payload = JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("client_update_id", clientUpdateId)
            .put("mission_id", missionId)
            .put("user_id", session.userId)
            .put("category", category)
            .put("detail", detail.take(4_000))
            .put("occurred_at", Instant.ofEpochMilli(occurredAtMs).toString())
        post(
            "/rest/v1/patrolgrid_field_updates?on_conflict=user_id,client_update_id",
            payload.toString(),
            session,
            ignoreDuplicates = true,
        )
        Unit
    }

    override fun signOut() = sessionRemote.signOut()

    private suspend fun authenticatedSession(): BackupSession = sessionRemote.authenticatedSession().getOrThrow()

    private fun loadIdentity(session: BackupSession): PatrolGridIdentity {
        val memberships = getRows(
            "/rest/v1/patrolgrid_memberships" +
                "?select=subdivision_id,role,display_name,badge_number" +
                "&user_id=eq.${encoded(session.userId)}&status=eq.active&limit=1",
            session,
        )
        check(memberships.length() == 1) {
            "Your PatrolGrid account is not assigned to an active subdivision. Contact your administrator."
        }
        val membership = memberships.getJSONObject(0)
        val subdivisionId = membership.getString("subdivision_id")
        val subdivisions = getRows(
            "/rest/v1/patrolgrid_subdivisions?select=name&id=eq.${encoded(subdivisionId)}&limit=1",
            session,
        )
        check(subdivisions.length() == 1) { "Your assigned subdivision is unavailable." }
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
        ignoreDuplicates: Boolean = false,
    ): String {
        val preferences = buildList {
            add(if (returnRows) "return=representation" else "return=minimal")
            if (ignoreDuplicates) add("resolution=ignore-duplicates")
        }.joinToString(",")
        val request = authorizedRequest(path, session)
            .header("Prefer", preferences)
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun patch(path: String, body: String, session: BackupSession): String {
        val request = authorizedRequest(path, session)
            .header("Prefer", "return=minimal")
            .patch(body.toRequestBody(JSON))
            .build()
        return execute(request)
    }

    private fun authorizedRequest(path: String, session: BackupSession): Request.Builder = Request.Builder()
        .url(configuration.baseUrl + path)
        .header("apikey", configuration.anonymousKey)
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("Content-Type", "application/json")

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            ensureSuccess(response.code)
            return response.body?.string().orEmpty()
        }
    }

    private fun ensureSuccess(code: Int) {
        if (code in 200..299) return
        val message = when (code) {
            400 -> "PatrolGrid rejected invalid mission data. Refresh and try again."
            401 -> "Your PatrolGrid session expired. Sign in again."
            403 -> "Your account is not authorized for this PatrolGrid action."
            404 -> "The requested patrol record is no longer available."
            409 -> "This patrol update was already recorded. Refresh to continue."
            429 -> "PatrolGrid is busy. Your local evidence is safe; retry shortly."
            in 500..599 -> "PatrolGrid service is temporarily unavailable."
            else -> "PatrolGrid request failed ($code)."
        }
        if (code == 401) sessionRemote.signOut()
        throw IllegalStateException(message)
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

    private companion object {
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
