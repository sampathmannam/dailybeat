package com.dailybeat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupCoordinator
import com.dailybeat.app.backup.EncryptedBackupSessionStore
import com.dailybeat.app.backup.LocalBackupStore
import com.dailybeat.app.backup.SupabaseBackupClient
import com.dailybeat.app.cloud.CloudLlmClient
import com.dailybeat.app.cloud.PulseReportGenerator
import com.dailybeat.app.cloud.ReportGenerator
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.MIGRATION_2_3
import com.dailybeat.app.data.db.MIGRATION_3_4
import com.dailybeat.app.data.db.MIGRATION_4_5
import com.dailybeat.app.data.db.migration5To6
import com.dailybeat.app.data.db.MIGRATION_6_7
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.PatrolGridRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.cloud.WeeklyReportGenerator
import com.dailybeat.app.export.PackageExporter
import com.dailybeat.app.export.PdfExporter
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.llm.EventExtractor
import com.dailybeat.app.security.PatrolTrackCipher
import com.dailybeat.app.patrolgrid.SupabasePatrolGridClient
import com.dailybeat.app.patrolgrid.PatrolTrackSyncer
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker
import com.dailybeat.app.patrolgrid.PatrolActionOutbox
import com.dailybeat.app.patrolgrid.PatrolGridSnapshotCache
import com.dailybeat.app.patrolgrid.PatrolEvidenceRetentionManager
import com.dailybeat.app.patrolgrid.PatrolEvidenceRetentionWorker
import com.dailybeat.app.patrolgrid.PATROLGRID_LOCAL_RETENTION_DAYS
import com.dailybeat.app.patrolgrid.PatrolMissionRetentionStore
import com.dailybeat.app.patrolgrid.reportPatrolRetentionEnforcementFailure
import com.dailybeat.app.patrolgrid.shouldSchedulePatrolRetentionWorkers
import com.dailybeat.app.capture.CaptureController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PatrolRetentionStartupState { CHECKING, RECOVERY_REQUIRED, READY, BLOCKED }

class DailyBeatApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _patrolRetentionStartupState = MutableStateFlow(PatrolRetentionStartupState.CHECKING)
    val patrolRetentionStartupState: StateFlow<PatrolRetentionStartupState> =
        _patrolRetentionStartupState.asStateFlow()

    val isPatrolGridConfigured: Boolean
        get() = BackupConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY).isConfigured

    val patrolTrackCipher: PatrolTrackCipher by lazy { PatrolTrackCipher(this) }

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db")
            .addMigrations(
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                migration5To6(patrolTrackCipher),
                MIGRATION_6_7,
            )
            .build()
    }

    val eventExtractor: EventExtractor by lazy { EventExtractor(cloudLlm, settingsRepository) }

    val eventRepository: EventRepository by lazy { EventRepository(db.events()) }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(db.diaries()) }

    val placeRepository: PlaceRepository by lazy { PlaceRepository(db.places()) }

    val visitRepository: VisitRepository by lazy { VisitRepository(db.visits()) }

    val patrolGridRepository: PatrolGridRepository by lazy {
        PatrolGridRepository(
            context = this,
            trackDao = db.patrolTracks(),
            settings = settingsRepository,
            coordinateDecoder = { point ->
                patrolTrackCipher.decrypt(
                    missionId = point.missionId,
                    timestampMs = point.timestampMs,
                    encryptedPayload = point.encryptedPayload,
                )
            },
        )
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    private val backupSessionStore by lazy { EncryptedBackupSessionStore(this) }

    private val localBackupStore by lazy { LocalBackupStore(db, settingsRepository) }

    private val backupClient by lazy {
        SupabaseBackupClient(
            configuration = BackupConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
            sessionStore = backupSessionStore,
        )
    }

    val patrolGridRemote by lazy {
        SupabasePatrolGridClient(
            configuration = BackupConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
            sessionRemote = backupClient,
        )
    }

    val patrolTrackSyncer by lazy {
        PatrolTrackSyncer(
            dao = db.patrolTracks(),
            cipher = patrolTrackCipher,
            remote = patrolGridRemote,
            settings = settingsRepository,
        )
    }

    val patrolActionOutbox by lazy { PatrolActionOutbox(this, patrolGridRemote) }

    internal val patrolMissionRetentionStore by lazy { PatrolMissionRetentionStore(this) }

    val patrolGridSnapshotCache by lazy {
        PatrolGridSnapshotCache(this, retentionStore = patrolMissionRetentionStore)
    }

    internal val patrolEvidenceRetentionManager by lazy {
        check(BuildConfig.PATROLGRID_RETENTION_DAYS == PATROLGRID_LOCAL_RETENTION_DAYS) {
            "PatrolGrid retention configuration does not match the device policy."
        }
        PatrolEvidenceRetentionManager(
            trackDao = db.patrolTracks(),
            actionOutbox = patrolActionOutbox,
            retentionStore = patrolMissionRetentionStore,
            snapshotCache = patrolGridSnapshotCache,
            settings = settingsRepository,
            retentionDays = BuildConfig.PATROLGRID_RETENTION_DAYS,
        )
    }

    val backupCoordinator by lazy { BackupCoordinator(localBackupStore, backupClient) }

    val pdfExporter: PdfExporter by lazy { PdfExporter(this) }

    val osmGeocoder: OsmGeocoder by lazy { OsmGeocoder(db.geocodes()) }

    val cloudLlm: CloudLlmClient by lazy { CloudLlmClient(settingsRepository.secureApiKey) }

    val reportGenerator: ReportGenerator by lazy {
        ReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
            appContext = this,
        )
    }

    val pulseGenerator: PulseReportGenerator by lazy {
        PulseReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
        )
    }

    val weeklyGenerator: WeeklyReportGenerator by lazy {
        WeeklyReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
        )
    }

    val packageExporter: PackageExporter by lazy {
        PackageExporter(this, diaryRepository, pdfExporter)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Immediate and daily retention work has no network constraint. Keep JVM
        // synthetic stores deterministic; release and on-device debug builds both run it.
        if (shouldSchedulePatrolRetentionWorkers(Build.FINGERPRINT)) {
            runPatrolRetentionStartupCheck()
            PatrolEvidenceRetentionWorker.schedule(this)
        } else {
            // Instrumented/JVM synthetic stores are controlled by their tests.
            _patrolRetentionStartupState.value = PatrolRetentionStartupState.READY
        }
        if (isPatrolGridConfigured) {
            PatrolTrackSyncWorker.scheduleSafetyNet(this)
            if (settingsRepository.get().pendingPatrolCloseSessionId != null) {
                PatrolTrackSyncWorker.enqueue(this)
            }
        }
    }

    fun retryPatrolRetentionStartupCheck() = runPatrolRetentionStartupCheck()

    /**
     * Authenticated recovery is intentionally separate from protected-content rendering.
     * It uploads/closes pending evidence, learns the server closure clock, then reruns
     * local cleanup before the app can become READY.
     */
    suspend fun recoverPatrolRetentionClock(): Result<Unit> = runCatching {
        val actionSync = patrolActionOutbox.syncPending()
        val routeSync = actionSync.fold(
            onSuccess = { patrolTrackSyncer.syncAndClosePendingSession() },
            onFailure = { kotlin.Result.failure(it) },
        )
        routeSync.exceptionOrNull()?.let { error ->
            val unavailable = error as? com.dailybeat.app.patrolgrid.PatrolEvidenceDestinationUnavailableException
                ?: throw error
            patrolEvidenceRetentionManager.discardUnavailableMission(unavailable.missionId).getOrThrow()
        }
        val settings = settingsRepository.get()
        val snapshot = patrolGridRemote.loadSnapshot(
            settings.activePatrolMissionId ?: settings.pendingPatrolCloseMissionId,
        ).getOrThrow()
        patrolGridSnapshotCache.save(snapshot)
        patrolEvidenceRetentionManager.enforce().getOrThrow()
        _patrolRetentionStartupState.value = PatrolRetentionStartupState.READY
        CaptureController.applyFromSettings(this@DailyBeatApp)
    }

    private fun runPatrolRetentionStartupCheck() {
        _patrolRetentionStartupState.value = PatrolRetentionStartupState.CHECKING
        applicationScope.launch {
            val result = runCatching { patrolEvidenceRetentionManager.enforce().getOrThrow() }
            if (result.exceptionOrNull() is com.dailybeat.app.patrolgrid.PatrolMissionClockUnavailableException) {
                if (isPatrolGridConfigured && patrolGridRemote.currentSession() != null) {
                    val recovered = recoverPatrolRetentionClock()
                    if (recovered.isSuccess) return@launch
                }
                _patrolRetentionStartupState.value = PatrolRetentionStartupState.RECOVERY_REQUIRED
                return@launch
            }
            if (result.isFailure) {
                runCatching {
                    reportPatrolRetentionEnforcementFailure(
                        settingsRepository,
                        System.currentTimeMillis(),
                    )
                }
            }
            _patrolRetentionStartupState.value = if (result.isSuccess) {
                PatrolRetentionStartupState.READY
            } else {
                PatrolRetentionStartupState.BLOCKED
            }
            if (result.isSuccess) CaptureController.applyFromSettings(this@DailyBeatApp)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                com.dailybeat.app.capture.LocationService.CHANNEL_ID,
                "Active patrol tracking",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
