package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place

data class BackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtMs: Long,
    val events: List<Event>,
    val places: List<Place>,
    val diaries: List<DiaryEntry>,
    val visits: List<LocationVisit>,
    val settings: BackupSettings,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        fun empty(createdAtMs: Long) = BackupSnapshot(
            createdAtMs = createdAtMs,
            events = emptyList(),
            places = emptyList(),
            diaries = emptyList(),
            visits = emptyList(),
            settings = BackupSettings(),
        )
    }
}

data class BackupSettings(
    val officerName: String = "IPS Officer",
    val gpsCaptureEnabled: Boolean = true,
    val callLogEnabled: Boolean = false,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = "deepseek",
    val cloudModel: String = "deepseek-chat",
    val cloudBaseUrl: String = "",
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
    val supervisorName: String = "",
)
