package com.dailybeat.app.backup

import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.model.BeatReview
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.model.DiaryRevision
import com.dailybeat.app.data.model.VisitCorrection

data class BackupSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtMs: Long,
    val events: List<Event>,
    val places: List<Place>,
    val diaries: List<DiaryEntry>,
    val visits: List<LocationVisit>,
    val settings: BackupSettings,
    val breadcrumbs: List<LocationBreadcrumb> = emptyList(),
    val beatReviews: List<BeatReview> = emptyList(),
    val diaryRevisions: List<DiaryRevision> = emptyList(),
    val visitCorrections: List<VisitCorrection> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 3

        fun empty(createdAtMs: Long) = BackupSnapshot(
            createdAtMs = createdAtMs,
            events = emptyList(),
            places = emptyList(),
            diaries = emptyList(),
            visits = emptyList(),
            settings = BackupSettings(),
            breadcrumbs = emptyList(),
            beatReviews = emptyList(),
            diaryRevisions = emptyList(),
            visitCorrections = emptyList(),
        )
    }
}

data class BackupSettings(
    val officerName: String = "IPS Officer",
    val themePreference: String = "system",
    val gpsCaptureEnabled: Boolean = true,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = "deepseek",
    val cloudModel: String = "deepseek-chat",
    val cloudBaseUrl: String = "",
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
    val supervisorName: String = "",
    val journalProfile: String = "personal",
    val historyRetentionDays: Int = 0,
)
