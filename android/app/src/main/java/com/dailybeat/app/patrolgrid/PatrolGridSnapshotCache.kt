// Snapshot persistence uses synchronous commit() to surface the boolean return
// for atomic acknowledgement. KTX edit returns Unit, so the non-KTX form is
// intentional.
@file:Suppress("UseKtx")

package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import org.json.JSONArray
import org.json.JSONObject

class PatrolGridSnapshotCache internal constructor(
    context: Context,
    private val retentionStore: PatrolMissionRetentionStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(snapshot: PatrolGridRemoteSnapshot) {
        retentionStore?.recordAuthoritativeMissions(snapshot.missions)
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("savedAtMs", clock())
            .put("identity", identityJson(snapshot.identity))
            .put("missions", JSONArray(snapshot.missions.map(::missionJson)))
            .put("evidenceMissionId", snapshot.evidenceMissionId ?: "")
            .put("recordedTrackPoints", snapshot.recordedTrackPoints)
            .put("observationCount", snapshot.observationCount)
            .put("routePoints", JSONArray(snapshot.routePoints.map(::mapPointJson)))
            .put("plannedRoutePoints", JSONArray(snapshot.plannedRoutePoints.map(::mapPointJson)))
            .put("reviewContextRequestId", snapshot.reviewContextRequestId ?: "")
            .put("reviewContextRequest", snapshot.reviewContextRequest ?: "")
            .put("reviewContextResponse", snapshot.reviewContextResponse ?: "")
            .put("evidenceSources", JSONArray(snapshot.evidenceSources.map(::evidenceSourceJson)))
            .put("selectedEvidenceSessionId", snapshot.selectedEvidenceSessionId ?: "")
            .put(
                "priorityVisitEvidence",
                JSONArray(snapshot.priorityVisitEvidence.map(::priorityVisitEvidenceJson)),
            )
        check(prefs.edit().putString(KEY_SNAPSHOT, root.toString()).commit()) {
            "Unable to cache the patrol briefing securely."
        }
    }

    fun load(userId: String, maxAgeMs: Long = MAX_AGE_MS): PatrolGridRemoteSnapshot? = runCatching {
        val root = JSONObject(prefs.getString(KEY_SNAPSHOT, null) ?: return null)
        if (root.getInt("schemaVersion") != SCHEMA_VERSION) {
            clear()
            return null
        }
        if (clock() - root.getLong("savedAtMs") !in 0..maxAgeMs) {
            clear()
            return null
        }
        val identity = identity(root.getJSONObject("identity"))
        if (identity.userId != userId) return null
        PatrolGridRemoteSnapshot(
            identity = identity,
            missions = root.getJSONArray("missions").objects(::mission),
            evidenceMissionId = root.optString("evidenceMissionId").takeIf(String::isNotBlank),
            recordedTrackPoints = root.getInt("recordedTrackPoints"),
            observationCount = root.getInt("observationCount"),
            routePoints = root.getJSONArray("routePoints").objects(::mapPoint),
            plannedRoutePoints = root.optJSONArray("plannedRoutePoints")?.objects(::mapPoint).orEmpty(),
            reviewContextRequestId = root.optString("reviewContextRequestId").takeIf(String::isNotBlank),
            reviewContextRequest = root.optString("reviewContextRequest").takeIf(String::isNotBlank),
            reviewContextResponse = root.optString("reviewContextResponse").takeIf(String::isNotBlank),
            evidenceSources = root.optJSONArray("evidenceSources")
                ?.objects(::evidenceSource)
                .orEmpty(),
            selectedEvidenceSessionId = root.optString("selectedEvidenceSessionId")
                .takeIf(String::isNotBlank),
            priorityVisitEvidence = root.optJSONArray("priorityVisitEvidence")
                ?.objects(::priorityVisitEvidence)
                .orEmpty(),
        )
    }.onFailure {
        // A damaged cache contains protected mission material that can no longer be
        // interpreted safely. Best-effort removal is followed by startup enforcement,
        // which fails closed if the secure preference itself cannot be cleared.
        clear()
    }.getOrNull()

    fun clear() {
        check(prefs.edit().remove(KEY_SNAPSHOT).commit()) {
            "Unable to clear the secure patrol briefing cache."
        }
    }

    /** Returns one aggregate protected-cache item when it is expired or malformed. */
    internal fun retentionDiscardCount(
        nowMs: Long,
        maxAgeMs: Long = MAX_AGE_MS,
        dueMissionIds: Set<String> = emptySet(),
    ): Int {
        require(nowMs > 0L && maxAgeMs > 0L)
        val encoded = try {
            prefs.getString(KEY_SNAPSHOT, null)
        } catch (_: Exception) {
            return 1
        } ?: return 0
        val root = runCatching { JSONObject(encoded) }.getOrNull() ?: return 1
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return 1
        val savedAtMs = runCatching { root.getLong("savedAtMs") }.getOrNull() ?: return 1
        if (savedAtMs <= 0L || savedAtMs > nowMs || nowMs - savedAtMs >= maxAgeMs) return 1
        val missions = root.optJSONArray("missions") ?: return 1
        val containsExpiredEvidence = runCatching {
            (0 until missions.length()).any { index ->
                val mission = missions.getJSONObject(index)
                val missionId = mission.getString("id")
                val rawDeadline = mission.opt("retentionUntilEpochMs")
                val deadlineMs = when (rawDeadline) {
                    null, JSONObject.NULL -> null
                    is Number -> rawDeadline.toLong().also { require(it > 0L) }
                    else -> error("Invalid cached retention deadline")
                }
                missionId in dueMissionIds || deadlineMs?.let { it <= nowMs } == true
            }
        }.getOrElse { return 1 }
        return if (containsExpiredEvidence) 1 else 0
    }

    internal fun purgeForRetentionIfNeeded(
        nowMs: Long,
        maxAgeMs: Long = MAX_AGE_MS,
        dueMissionIds: Set<String> = emptySet(),
    ): Int {
        val count = retentionDiscardCount(nowMs, maxAgeMs, dueMissionIds)
        if (count > 0) clear()
        return count
    }

    internal fun discardProtectedSnapshot(): Int {
        val present = protectedSnapshotCount() > 0
        if (present) clear()
        return if (present) 1 else 0
    }

    internal fun protectedSnapshotCount(): Int =
        if (runCatching { prefs.getString(KEY_SNAPSHOT, null) != null }.getOrDefault(true)) 1 else 0

    private fun identityJson(value: PatrolGridIdentity) = JSONObject()
        .put("userId", value.userId)
        .put("subdivisionId", value.subdivisionId)
        .put("subdivisionName", value.subdivisionName)
        .put("displayName", value.displayName)
        .put("badgeNumber", value.badgeNumber ?: "")
        .put("role", value.role.storageValue)

    private fun identity(value: JSONObject) = PatrolGridIdentity(
        userId = value.getString("userId"),
        subdivisionId = value.getString("subdivisionId"),
        subdivisionName = value.getString("subdivisionName"),
        displayName = value.getString("displayName"),
        badgeNumber = value.optString("badgeNumber").takeIf(String::isNotBlank),
        role = PatrolRole.fromStorage(value.getString("role")),
    )

    private fun missionJson(value: PatrolMission) = JSONObject()
        .put("id", value.id)
        .put("title", value.title)
        .put("dutyWindow", value.dutyWindow)
        .put("unitName", value.unitName)
        .put("personnelCount", value.personnelCount)
        .put("status", value.status.name)
        .put("statusLabel", value.statusLabel)
        .put("context", value.context)
        .put("priorityLocations", JSONArray(value.priorityLocations.map(::priorityJson)))
        .put("lastUpdateLabel", value.lastUpdateLabel)
        .put("hasOperationalDeviation", value.hasOperationalDeviation)
        .put("version", value.version)
        .put("endsAtEpochMs", value.endsAtEpochMs ?: JSONObject.NULL)
        .put("retentionUntilEpochMs", value.retentionUntilEpochMs ?: JSONObject.NULL)

    private fun mission(value: JSONObject) = PatrolMission(
        id = value.getString("id"),
        title = value.getString("title"),
        dutyWindow = value.getString("dutyWindow"),
        unitName = value.getString("unitName"),
        personnelCount = value.getInt("personnelCount"),
        status = PatrolMissionStatus.valueOf(value.getString("status")),
        statusLabel = value.getString("statusLabel"),
        context = value.getString("context"),
        priorityLocations = value.getJSONArray("priorityLocations").objects(::priority),
        lastUpdateLabel = value.getString("lastUpdateLabel"),
        hasOperationalDeviation = value.getBoolean("hasOperationalDeviation"),
        version = value.optInt("version", 1),
        endsAtEpochMs = value.optLong("endsAtEpochMs").takeIf { it > 0L },
        retentionUntilEpochMs = value.optLong("retentionUntilEpochMs").takeIf { it > 0L },
    )

    private fun priorityJson(value: PriorityLocation) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("state", value.state.name)
        .put("detail", value.detail)
        .put("required", value.required)
        .put("latitude", value.latitude ?: JSONObject.NULL)
        .put("longitude", value.longitude ?: JSONObject.NULL)
        .put("radiusM", value.radiusM ?: JSONObject.NULL)

    private fun priority(value: JSONObject) = PriorityLocation(
        id = value.getString("id"),
        name = value.getString("name"),
        state = PriorityLocationState.valueOf(value.getString("state")),
        detail = value.getString("detail"),
        required = value.getBoolean("required"),
        latitude = value.optDouble("latitude").takeUnless(Double::isNaN),
        longitude = value.optDouble("longitude").takeUnless(Double::isNaN),
        radiusM = value.optDouble("radiusM").takeUnless(Double::isNaN),
    )

    private fun mapPointJson(value: PatrolMapPoint) = JSONObject()
        .put("latitude", value.latitude)
        .put("longitude", value.longitude)

    private fun mapPoint(value: JSONObject) = PatrolMapPoint(
        latitude = value.getDouble("latitude"),
        longitude = value.getDouble("longitude"),
    )

    private fun evidenceSourceJson(value: PatrolEvidenceSource) = JSONObject()
        .put("sessionId", value.sessionId)
        .put("userId", value.userId)
        .put("displayName", value.displayName)
        .put("badgeNumber", value.badgeNumber ?: "")
        .put("startedAtMs", value.startedAtMs)
        .put("endedAtMs", value.endedAtMs ?: JSONObject.NULL)
        .put("endReason", value.endReason ?: "")
        .put("appVersion", value.appVersion)
        .put("trackPointCount", value.trackPointCount)
        .put("firstRecordedAtMs", value.firstRecordedAtMs ?: JSONObject.NULL)
        .put("lastRecordedAtMs", value.lastRecordedAtMs ?: JSONObject.NULL)
        .put("firstReceivedAtMs", value.firstReceivedAtMs ?: JSONObject.NULL)
        .put("lastReceivedAtMs", value.lastReceivedAtMs ?: JSONObject.NULL)
        .put("bestAccuracyM", value.bestAccuracyM ?: JSONObject.NULL)
        .put("worstAccuracyM", value.worstAccuracyM ?: JSONObject.NULL)

    private fun evidenceSource(value: JSONObject) = PatrolEvidenceSource(
        sessionId = value.getString("sessionId"),
        userId = value.getString("userId"),
        displayName = value.getString("displayName"),
        badgeNumber = value.optString("badgeNumber").takeIf(String::isNotBlank),
        startedAtMs = value.getLong("startedAtMs"),
        endedAtMs = value.nullablePositiveLong("endedAtMs"),
        endReason = value.optString("endReason").takeIf(String::isNotBlank),
        appVersion = value.getString("appVersion"),
        trackPointCount = value.getInt("trackPointCount"),
        firstRecordedAtMs = value.nullablePositiveLong("firstRecordedAtMs"),
        lastRecordedAtMs = value.nullablePositiveLong("lastRecordedAtMs"),
        firstReceivedAtMs = value.nullablePositiveLong("firstReceivedAtMs"),
        lastReceivedAtMs = value.nullablePositiveLong("lastReceivedAtMs"),
        bestAccuracyM = value.nullableFloat("bestAccuracyM"),
        worstAccuracyM = value.nullableFloat("worstAccuracyM"),
    )

    private fun priorityVisitEvidenceJson(value: PatrolPriorityVisitEvidence) = JSONObject()
        .put("sessionId", value.sessionId)
        .put("priorityLocationId", value.priorityLocationId)
        .put("priorityName", value.priorityName)
        .put("userId", value.userId)
        .put("displayName", value.displayName)
        .put("visitedAtMs", value.visitedAtMs)
        .put("receivedAtMs", value.receivedAtMs)
        .put("method", value.method)
        .put("accuracyM", value.accuracyM ?: JSONObject.NULL)
        .put("note", value.note ?: "")

    private fun priorityVisitEvidence(value: JSONObject) = PatrolPriorityVisitEvidence(
        sessionId = value.getString("sessionId"),
        priorityLocationId = value.getString("priorityLocationId"),
        priorityName = value.getString("priorityName"),
        userId = value.getString("userId"),
        displayName = value.getString("displayName"),
        visitedAtMs = value.getLong("visitedAtMs"),
        receivedAtMs = value.getLong("receivedAtMs"),
        method = value.getString("method"),
        accuracyM = value.nullableFloat("accuracyM"),
        note = value.optString("note").takeIf(String::isNotBlank),
    )

    private fun JSONObject.nullablePositiveLong(key: String): Long? =
        takeUnless { isNull(key) }?.optLong(key)?.takeIf { it > 0L }

    private fun JSONObject.nullableFloat(key: String): Float? =
        takeUnless { isNull(key) }?.optDouble(key)?.takeUnless(Double::isNaN)?.toFloat()

    private fun <T> JSONArray.objects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }

    private companion object {
        const val FILE_NAME = "patrolgrid_mission_cache"
        const val KEY_SNAPSHOT = "authorized_snapshot"
        // Version 2 is the first format whose route points are bound to exactly one
        // person/session. Rejecting older caches prevents an upgraded app from showing
        // the legacy mission-wide merged trail while offline.
        const val SCHEMA_VERSION = 2
        const val MAX_AGE_MS = 24 * 60 * 60 * 1_000L
    }
}
