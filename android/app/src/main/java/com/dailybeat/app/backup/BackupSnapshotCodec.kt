package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BackupSnapshotCodec {

    fun encode(snapshot: BackupSnapshot): String = JSONObject().apply {
        put("schemaVersion", snapshot.schemaVersion)
        put("createdAtMs", snapshot.createdAtMs)
        put("events", JSONArray(snapshot.events.map(::eventJson)))
        put("places", JSONArray(snapshot.places.map(::placeJson)))
        put("diaries", JSONArray(snapshot.diaries.map(::diaryJson)))
        put("visits", JSONArray(snapshot.visits.map(::visitJson)))
        put("settings", settingsJson(snapshot.settings))
    }.toString()

    fun decode(json: String): BackupSnapshot {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw IllegalArgumentException("Backup is not valid JSON.")
        }

        val version = root.optInt("schemaVersion", -1)
        if (version != BackupSnapshot.CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version")
        }

        return try {
            BackupSnapshot(
                schemaVersion = version,
                createdAtMs = root.getLong("createdAtMs"),
                events = root.getJSONArray("events").mapObjects(::event),
                places = root.getJSONArray("places").mapObjects(::place),
                diaries = root.getJSONArray("diaries").mapObjects(::diary),
                visits = root.getJSONArray("visits").mapObjects(::visit),
                settings = settings(root.getJSONObject("settings")),
            )
        } catch (_: JSONException) {
            throw IllegalArgumentException("Backup is incomplete or damaged.")
        }
    }

    private fun eventJson(value: Event) = JSONObject().apply {
        put("id", value.id)
        put("timestamp", value.timestamp)
        put("type", value.type)
        put("rawText", value.rawText)
        putNullable("placeName", value.placeName)
        putNullable("latitude", value.latitude)
        putNullable("longitude", value.longitude)
        putNullable("peopleMentioned", value.peopleMentioned)
        putNullable("caseNumbers", value.caseNumbers)
        putNullable("sourceId", value.sourceId)
    }

    private fun event(value: JSONObject) = Event(
        id = value.getLong("id"),
        timestamp = value.getLong("timestamp"),
        type = value.getString("type"),
        rawText = value.getString("rawText"),
        placeName = value.nullableString("placeName"),
        latitude = value.nullableDouble("latitude"),
        longitude = value.nullableDouble("longitude"),
        peopleMentioned = value.nullableString("peopleMentioned"),
        caseNumbers = value.nullableString("caseNumbers"),
        sourceId = value.nullableString("sourceId"),
    )

    private fun placeJson(value: Place) = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("latitude", value.latitude)
        put("longitude", value.longitude)
        put("radiusM", value.radiusM)
    }

    private fun place(value: JSONObject) = Place(
        id = value.getLong("id"),
        name = value.getString("name"),
        latitude = value.getDouble("latitude"),
        longitude = value.getDouble("longitude"),
        radiusM = value.getInt("radiusM"),
    )

    private fun diaryJson(value: DiaryEntry) = JSONObject().apply {
        put("dateKey", value.dateKey)
        put("text", value.text)
        put("updatedAt", value.updatedAt)
    }

    private fun diary(value: JSONObject) = DiaryEntry(
        dateKey = value.getString("dateKey"),
        text = value.getString("text"),
        updatedAt = value.getLong("updatedAt"),
    )

    private fun visitJson(value: LocationVisit) = JSONObject().apply {
        put("id", value.id)
        put("startMs", value.startMs)
        put("endMs", value.endMs)
        put("latitude", value.latitude)
        put("longitude", value.longitude)
        putNullable("placeName", value.placeName)
        putNullable("address", value.address)
        put("visitType", value.visitType)
    }

    private fun visit(value: JSONObject) = LocationVisit(
        id = value.getLong("id"),
        startMs = value.getLong("startMs"),
        endMs = value.getLong("endMs"),
        latitude = value.getDouble("latitude"),
        longitude = value.getDouble("longitude"),
        placeName = value.nullableString("placeName"),
        address = value.nullableString("address"),
        visitType = value.getString("visitType"),
    )

    private fun settingsJson(value: BackupSettings) = JSONObject().apply {
        put("officerName", value.officerName)
        put("gpsCaptureEnabled", value.gpsCaptureEnabled)
        put("callLogEnabled", value.callLogEnabled)
        put("cloudLlmEnabled", value.cloudLlmEnabled)
        put("cloudProvider", value.cloudProvider)
        put("cloudModel", value.cloudModel)
        put("cloudBaseUrl", value.cloudBaseUrl)
        put("autoEveningReport", value.autoEveningReport)
        put("autoMiddayPulse", value.autoMiddayPulse)
        put("supervisorName", value.supervisorName)
    }

    private fun settings(value: JSONObject) = BackupSettings(
        officerName = value.getString("officerName"),
        gpsCaptureEnabled = value.getBoolean("gpsCaptureEnabled"),
        callLogEnabled = value.getBoolean("callLogEnabled"),
        cloudLlmEnabled = value.getBoolean("cloudLlmEnabled"),
        cloudProvider = value.getString("cloudProvider"),
        cloudModel = value.getString("cloudModel"),
        cloudBaseUrl = value.getString("cloudBaseUrl"),
        autoEveningReport = value.getBoolean("autoEveningReport"),
        autoMiddayPulse = value.getBoolean("autoMiddayPulse"),
        supervisorName = value.getString("supervisorName"),
    )

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }
}
