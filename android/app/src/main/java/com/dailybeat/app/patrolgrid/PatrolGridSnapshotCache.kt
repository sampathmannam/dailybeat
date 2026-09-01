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

class PatrolGridSnapshotCache(
    context: Context,
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
        val root = JSONObject()
            .put("savedAtMs", clock())
            .put("identity", identityJson(snapshot.identity))
            .put("missions", JSONArray(snapshot.missions.map(::missionJson)))
            .put("recordedTrackPoints", snapshot.recordedTrackPoints)
            .put("observationCount", snapshot.observationCount)
            .put("routePoints", JSONArray(snapshot.routePoints.map(::mapPointJson)))
        check(prefs.edit().putString(KEY_SNAPSHOT, root.toString()).commit()) {
            "Unable to cache the patrol briefing securely."
        }
    }

    fun load(userId: String, maxAgeMs: Long = MAX_AGE_MS): PatrolGridRemoteSnapshot? = runCatching {
        val root = JSONObject(prefs.getString(KEY_SNAPSHOT, null) ?: return null)
        if (clock() - root.getLong("savedAtMs") !in 0..maxAgeMs) return null
        val identity = identity(root.getJSONObject("identity"))
        if (identity.userId != userId) return null
        PatrolGridRemoteSnapshot(
            identity = identity,
            missions = root.getJSONArray("missions").objects(::mission),
            recordedTrackPoints = root.getInt("recordedTrackPoints"),
            observationCount = root.getInt("observationCount"),
            routePoints = root.getJSONArray("routePoints").objects(::mapPoint),
        )
    }.getOrNull()

    fun clear() {
        prefs.edit().clear().commit()
    }

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
    )

    private fun priorityJson(value: PriorityLocation) = JSONObject()
        .put("id", value.id)
        .put("name", value.name)
        .put("state", value.state.name)
        .put("detail", value.detail)
        .put("required", value.required)

    private fun priority(value: JSONObject) = PriorityLocation(
        id = value.getString("id"),
        name = value.getString("name"),
        state = PriorityLocationState.valueOf(value.getString("state")),
        detail = value.getString("detail"),
        required = value.getBoolean("required"),
    )

    private fun mapPointJson(value: PatrolMapPoint) = JSONObject()
        .put("latitude", value.latitude)
        .put("longitude", value.longitude)

    private fun mapPoint(value: JSONObject) = PatrolMapPoint(
        latitude = value.getDouble("latitude"),
        longitude = value.getDouble("longitude"),
    )

    private fun <T> JSONArray.objects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }

    private companion object {
        const val FILE_NAME = "patrolgrid_mission_cache"
        const val KEY_SNAPSHOT = "authorized_snapshot"
        const val MAX_AGE_MS = 24 * 60 * 60 * 1_000L
    }
}
