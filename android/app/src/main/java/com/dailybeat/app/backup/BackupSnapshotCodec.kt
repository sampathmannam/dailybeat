package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

object BackupSnapshotCodec {

    fun encode(snapshot: BackupSnapshot): String {
        validate(snapshot)
        return JSONObject().apply {
            put("schemaVersion", snapshot.schemaVersion)
            put("createdAtMs", snapshot.createdAtMs)
            put("events", JSONArray(snapshot.events.map(::eventJson)))
            put("places", JSONArray(snapshot.places.map(::placeJson)))
            put("diaries", JSONArray(snapshot.diaries.map(::diaryJson)))
            put("visits", JSONArray(snapshot.visits.map(::visitJson)))
            put("settings", settingsJson(snapshot.settings))
        }.toString().also { encoded ->
            require(encoded.length <= MAX_JSON_CHARS) { "Backup is too large to upload safely." }
        }
    }

    fun decode(json: String): BackupSnapshot {
        require(json.length <= MAX_JSON_CHARS) { "Backup is too large to restore safely." }
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw IllegalArgumentException("Backup is not valid JSON.")
        }

        val version = root.optInt("schemaVersion", -1)
        if (version != BackupSnapshot.CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Unsupported backup version: $version")
        }

        val snapshot = try {
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
        validate(snapshot)
        return snapshot
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
        // Retired in 3.6.0. Still written because releases before it read this key strictly and
        // would fail to restore a backup taken on a newer phone.
        put("callLogEnabled", false)
        put("cloudLlmEnabled", value.cloudLlmEnabled)
        put("cloudProvider", value.cloudProvider)
        put("cloudModel", value.cloudModel)
        put("cloudBaseUrl", value.cloudBaseUrl)
        put("autoEveningReport", value.autoEveningReport)
        put("autoMiddayPulse", value.autoMiddayPulse)
        put("supervisorName", value.supervisorName)
    }

    /**
     * Settings are read leniently, with the defaults from [BackupSettings] filling any gap. A
     * preference that a later release adds or retires must never make an existing backup
     * unrestorable; the records themselves are still read strictly.
     */
    private fun settings(value: JSONObject): BackupSettings {
        val defaults = BackupSettings()
        return BackupSettings(
            officerName = value.optString("officerName").ifBlank { defaults.officerName },
            gpsCaptureEnabled = value.optBoolean("gpsCaptureEnabled", defaults.gpsCaptureEnabled),
            cloudLlmEnabled = value.optBoolean("cloudLlmEnabled", defaults.cloudLlmEnabled),
            cloudProvider = value.optString("cloudProvider").ifBlank { defaults.cloudProvider },
            cloudModel = value.optString("cloudModel").ifBlank { defaults.cloudModel },
            cloudBaseUrl = value.optString("cloudBaseUrl", defaults.cloudBaseUrl),
            autoEveningReport = value.optBoolean("autoEveningReport", defaults.autoEveningReport),
            autoMiddayPulse = value.optBoolean("autoMiddayPulse", defaults.autoMiddayPulse),
            supervisorName = value.optString("supervisorName", defaults.supervisorName),
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.nullableDouble(name: String): Double? =
        if (isNull(name)) null else getDouble(name)

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> {
        require(length() <= MAX_RECORDS_PER_TABLE) { "Backup contains too many records." }
        return (0 until length()).map { mapper(getJSONObject(it)) }
    }

    private fun validate(snapshot: BackupSnapshot) {
        require(snapshot.createdAtMs >= 0) { "Backup contains an invalid creation time." }
        require(snapshot.events.size <= MAX_RECORDS_PER_TABLE) { "Backup contains too many events." }
        require(snapshot.places.size <= MAX_PLACES) { "Backup contains too many places." }
        require(snapshot.diaries.size <= MAX_DIARIES) { "Backup contains too many diaries." }
        require(snapshot.visits.size <= MAX_RECORDS_PER_TABLE) { "Backup contains too many visits." }

        snapshot.events.forEach { event ->
            require(event.timestamp >= 0) { "Backup contains an invalid event time." }
            require(event.type.length <= MAX_SHORT_TEXT && event.rawText.length <= MAX_LONG_TEXT) {
                "Backup contains an oversized event."
            }
            validateCoordinates(event.latitude, event.longitude)
        }
        snapshot.places.forEach { place ->
            require(place.name.isNotBlank() && place.name.length <= MAX_SHORT_TEXT) {
                "Backup contains an invalid place name."
            }
            validateCoordinates(place.latitude, place.longitude)
            require(place.radiusM in 1..100_000) { "Backup contains an invalid place radius." }
        }
        snapshot.diaries.forEach { diary ->
            require(runCatching { LocalDate.parse(diary.dateKey) }.isSuccess) {
                "Backup contains an invalid diary date."
            }
            require(diary.text.length <= MAX_DIARY_TEXT && diary.updatedAt >= 0) {
                "Backup contains an invalid diary entry."
            }
        }
        snapshot.visits.forEach { visit ->
            require(visit.startMs >= 0 && visit.endMs >= visit.startMs) {
                "Backup contains an invalid visit time range."
            }
            validateCoordinates(visit.latitude, visit.longitude)
            require(visit.visitType.length <= MAX_SHORT_TEXT) { "Backup contains an invalid visit type." }
        }
        require(snapshot.settings.officerName.length <= MAX_SHORT_TEXT) {
            "Backup contains an oversized officer name."
        }
        require(snapshot.settings.supervisorName.length <= MAX_SHORT_TEXT) {
            "Backup contains an oversized supervisor name."
        }
        require(snapshot.settings.cloudBaseUrl.length <= MAX_URL_TEXT) {
            "Backup contains an oversized cloud URL."
        }
        require(snapshot.settings.cloudModel.length <= MAX_SHORT_TEXT) {
            "Backup contains an oversized cloud model."
        }
        require(snapshot.settings.cloudProvider in setOf("deepseek", "openai", "anthropic", "compatible")) {
            "Backup contains an unsupported cloud provider."
        }
    }

    private fun validateCoordinates(latitude: Double?, longitude: Double?) {
        require((latitude == null) == (longitude == null)) {
            "Backup contains incomplete coordinates."
        }
        if (latitude != null && longitude != null) {
            require(latitude.isFinite() && latitude in -90.0..90.0) {
                "Backup contains an invalid latitude."
            }
            require(longitude.isFinite() && longitude in -180.0..180.0) {
                "Backup contains an invalid longitude."
            }
        }
    }

    private const val MAX_JSON_CHARS = 10_000_000
    private const val MAX_RECORDS_PER_TABLE = 100_000
    private const val MAX_PLACES = 10_000
    private const val MAX_DIARIES = 10_000
    private const val MAX_SHORT_TEXT = 1_000
    private const val MAX_LONG_TEXT = 100_000
    private const val MAX_DIARY_TEXT = 500_000
    private const val MAX_URL_TEXT = 4_096
}
