package com.dailybeat.app.backup

import androidx.room.withTransaction
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.settings.SettingsRepository

interface SnapshotStore {
    suspend fun createSnapshot(): BackupSnapshot
    suspend fun restore(snapshot: BackupSnapshot)
}

class LocalBackupStore(
    private val db: DailyBeatDb,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : SnapshotStore {
    override suspend fun createSnapshot(): BackupSnapshot {
        val settings = settingsRepository.get()
        return BackupSnapshot(
            createdAtMs = clock(),
            events = db.events().all(),
            places = db.places().all(),
            diaries = db.diaries().all(),
            visits = db.visits().all(),
            settings = BackupSettings(
                officerName = settings.officerName,
                gpsCaptureEnabled = settings.gpsCaptureEnabled,
                callLogEnabled = settings.callLogEnabled,
                cloudLlmEnabled = settings.cloudLlmEnabled,
                cloudProvider = settings.cloudProvider,
                cloudModel = settings.cloudModel,
                cloudBaseUrl = settings.cloudBaseUrl,
                autoEveningReport = settings.autoEveningReport,
                autoMiddayPulse = settings.autoMiddayPulse,
                supervisorName = settings.supervisorName,
            ),
        )
    }

    override suspend fun restore(snapshot: BackupSnapshot) {
        require(snapshot.schemaVersion == BackupSnapshot.CURRENT_SCHEMA_VERSION) {
            "Unsupported backup version: ${snapshot.schemaVersion}"
        }
        db.withTransaction {
            db.events().deleteAll()
            db.places().deleteAll()
            db.diaries().deleteAll()
            db.visits().deleteAll()
            db.events().insertAll(snapshot.events)
            db.places().insertAll(snapshot.places)
            db.diaries().insertAll(snapshot.diaries)
            db.visits().insertAll(snapshot.visits)
        }
        applySettings(snapshot.settings)
    }

    private fun applySettings(settings: BackupSettings) {
        settingsRepository.setOfficerName(settings.officerName)
        settingsRepository.setGpsEnabled(settings.gpsCaptureEnabled)
        settingsRepository.setCallLogEnabled(settings.callLogEnabled)
        settingsRepository.setCloudLlmEnabled(settings.cloudLlmEnabled)
        settingsRepository.setCloudProvider(settings.cloudProvider)
        settingsRepository.setCloudModel(settings.cloudModel)
        settingsRepository.setCloudBaseUrl(settings.cloudBaseUrl)
        settingsRepository.setAutoEveningReport(settings.autoEveningReport)
        settingsRepository.setAutoMiddayPulse(settings.autoMiddayPulse)
        settingsRepository.setSupervisorName(settings.supervisorName)
    }
}
