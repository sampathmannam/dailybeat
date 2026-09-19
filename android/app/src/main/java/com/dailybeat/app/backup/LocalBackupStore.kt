package com.dailybeat.app.backup

import androidx.room.withTransaction
import com.dailybeat.app.capture.CaptureStorageGate
import kotlinx.coroutines.sync.withLock
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.data.settings.ThemePreference

interface SnapshotStore {
    suspend fun createSnapshot(): BackupSnapshot
    suspend fun restore(snapshot: BackupSnapshot)
}

class LocalBackupStore(
    private val db: DailyBeatDb,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : PagedSnapshotStore {
    private fun metadata(): BackupSnapshot {
        val settings = settingsRepository.get()
        return BackupSnapshot.empty(clock()).copy(settings = BackupSettings(
                    journalProfile = settings.journalProfile.id,
                    officerName = settings.officerName,
                    themePreference = settings.themePreference.id,
                    gpsCaptureEnabled = settings.gpsCaptureEnabled,
                    cloudLlmEnabled = settings.cloudLlmEnabled,
                    cloudProvider = settings.cloudProvider,
                    cloudModel = settings.cloudModel,
                    cloudBaseUrl = settings.cloudBaseUrl,
                    autoEveningReport = settings.autoEveningReport,
                    autoMiddayPulse = settings.autoMiddayPulse,
                    supervisorName = settings.supervisorName,
                    historyRetentionDays = settings.historyRetentionDays,
                ))
    }

    override suspend fun createSnapshot(): BackupSnapshot = db.withTransaction {
        metadata().copy(events = db.events().all(), places = db.places().all(), diaries = db.diaries().all(),
            visits = db.visits().all(), breadcrumbs = db.breadcrumbs().all(), beatReviews = db.beatReviews().all(),
            diaryRevisions = db.diaries().allRevisions(), visitCorrections = db.visits().allCorrections())
    }

    override suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit) = db.withTransaction {
        val metadata = metadata()
        emit(metadata)
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().events(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(events = rows))
                after = rows.last().id
            }
        }
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().places(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(places = rows))
                after = rows.last().id
            }
        }
        run {
            var after: String = ""
            while (true) {
                val rows = db.backupPages().diaries(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(diaries = rows))
                after = rows.last().dateKey
            }
        }
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().visits(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(visits = rows))
                after = rows.last().id
            }
        }
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().breadcrumbs(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(breadcrumbs = rows))
                after = rows.last().id
            }
        }
        run {
            var after: String = ""
            while (true) {
                val rows = db.backupPages().beatReviews(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(beatReviews = rows))
                after = rows.last().dateKey
            }
        }
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().diaryRevisions(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(diaryRevisions = rows))
                after = rows.last().id
            }
        }
        run {
            var after: Long? = null
            while (true) {
                val rows = db.backupPages().visitCorrections(after)
                if (rows.isEmpty()) break
                emit(metadata.copy(visitCorrections = rows))
                after = rows.last().id
            }
        }
    }

    override suspend fun restore(snapshot: BackupSnapshot) = restorePages(sequenceOf(snapshot))

    override suspend fun restorePages(pages: Sequence<BackupSnapshot>) {
        CaptureStorageGate.mutex.withLock {
            CaptureStorageGate.generation.incrementAndGet()
            var settings: BackupSettings? = null
            db.withTransaction {
                db.captureJournal().clearInbox()
                db.captureJournal().clearCheckpoint()
                db.events().deleteAll(); db.places().deleteAll(); db.diaries().deleteAll()
                db.visits().deleteAll(); db.breadcrumbs().deleteAll(); db.beatReviews().deleteAll()
                db.diaries().deleteAllRevisions(); db.visits().deleteAllCorrections()
                for (snapshot in pages) {
                    require(snapshot.schemaVersion == BackupSnapshot.CURRENT_SCHEMA_VERSION) { "Unsupported backup version." }
                    if (settings == null) settings = snapshot.settings
                    require(settings == snapshot.settings) { "Backup pages have inconsistent settings." }
                    db.events().insertAll(snapshot.events); db.places().insertAll(snapshot.places)
                    db.diaries().insertAll(snapshot.diaries); db.visits().insertAll(snapshot.visits)
                    db.breadcrumbs().insertAll(snapshot.breadcrumbs); db.beatReviews().insertAll(snapshot.beatReviews)
                    db.diaries().insertRevisions(snapshot.diaryRevisions); db.visits().insertCorrections(snapshot.visitCorrections)
                }
                requireNotNull(settings) { "Backup has no pages." }
                if (settings!!.historyRetentionDays > 0) {
                    com.dailybeat.app.data.retention.HistoryRetentionManager(db).pruneInsideCaptureLock(settings!!.historyRetentionDays)
                }
            }
            applySettings(requireNotNull(settings))
        }
    }

    private fun applySettings(settings: BackupSettings) {
        settingsRepository.setJournalProfile(com.dailybeat.app.data.settings.JournalProfile.fromId(settings.journalProfile))
        settingsRepository.setOfficerName(settings.officerName)
        settingsRepository.setThemePreference(ThemePreference.fromId(settings.themePreference))
        settingsRepository.setGpsEnabled(settings.gpsCaptureEnabled)
        settingsRepository.setCloudLlmEnabled(settings.cloudLlmEnabled)
        settingsRepository.setCloudProvider(settings.cloudProvider)
        settingsRepository.setCloudModel(settings.cloudModel)
        settingsRepository.setCloudBaseUrl(settings.cloudBaseUrl)
        // Restore is not renewed consent to unattended cloud generation on a new phone.
        settingsRepository.setAutoEveningReport(false)
        settingsRepository.setAutoMiddayPulse(settings.autoMiddayPulse)
        settingsRepository.setSupervisorName(settings.supervisorName)
        settingsRepository.setHistoryRetentionDays(settings.historyRetentionDays)
    }
}
