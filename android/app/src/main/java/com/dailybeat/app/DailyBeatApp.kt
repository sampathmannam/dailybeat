package com.dailybeat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupCoordinator
import com.dailybeat.app.backup.EncryptedBackupSessionStore
import com.dailybeat.app.backup.LocalBackupStore
import com.dailybeat.app.backup.SupabaseBackupClient
import com.dailybeat.app.cloud.CloudLlmClient
import com.dailybeat.app.cloud.PulseReportGenerator
import com.dailybeat.app.cloud.ReportGenerator
import com.dailybeat.app.cloud.ValidatedReportClient
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.DailyBeatMigrations
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.repo.BreadcrumbRepository
import com.dailybeat.app.data.repo.BeatRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.cloud.WeeklyReportGenerator
import com.dailybeat.app.export.PackageExporter
import com.dailybeat.app.export.PdfExporter
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.llm.EventExtractor
import com.dailybeat.app.notify.DailyReminderScheduler
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.capture.CaptureHealthStore
import com.dailybeat.app.data.retention.HistoryRetentionManager
import com.dailybeat.app.data.retention.HistoryRetentionWorker
import com.dailybeat.app.data.retention.LocalDataEraser

class DailyBeatApp : Application() {

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db")
            .addMigrations(*DailyBeatMigrations.ALL)
            .build()
    }

    val eventExtractor: EventExtractor by lazy { EventExtractor(cloudLlm, settingsRepository, ::permitsUnlinkedCloudText) }

    suspend fun permitsUnlinkedCloudText(): Boolean =
        placeRepository.all().none { it.isPrivate } && db.visits().hiddenEntries().isEmpty()

    val eventRepository: EventRepository by lazy { EventRepository(db.events()) }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(db.diaries()) }

    val placeRepository: PlaceRepository by lazy { PlaceRepository(db.places()) }

    val visitRepository: VisitRepository by lazy { VisitRepository(db.visits(), db) }

    val breadcrumbRepository: BreadcrumbRepository by lazy { BreadcrumbRepository(db.breadcrumbs()) }

    val beatRepository: BeatRepository by lazy { BeatRepository(db.beatReviews()) }

    val captureProcessor by lazy { com.dailybeat.app.capture.CaptureProcessor(db, osmGeocoder) }

    val captureHealthStore: CaptureHealthStore by lazy { CaptureHealthStore(this) }

    val historyRetentionManager by lazy { HistoryRetentionManager(db) }

    val localDataEraser by lazy { LocalDataEraser(this) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val mapSettings by lazy { com.dailybeat.app.maps.MapSettingsRepository(this) }
    val mapNetwork by lazy { com.dailybeat.app.maps.MapNetwork(this, mapSettings) }
    val offlineMaps by lazy { com.dailybeat.app.maps.OfflineMapRepository(this) }

    private val backupSessionStore by lazy { EncryptedBackupSessionStore(this) }

    private val localBackupStore by lazy { LocalBackupStore(db, settingsRepository) }

    private val backupClient by lazy {
        SupabaseBackupClient(
            configuration = BackupConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
            sessionStore = backupSessionStore,
        )
    }

    val backupCoordinator by lazy { BackupCoordinator(localBackupStore, backupClient, java.io.File(cacheDir, "encrypted-backup-staging")) }

    val pdfExporter: PdfExporter by lazy { PdfExporter(this) }

    val osmGeocoder: OsmGeocoder by lazy { OsmGeocoder(db.geocodes(), endpoint = settingsRepository::geocodingEndpoint,
        permitsLookup = { lat, lon -> !com.dailybeat.app.domain.OutboundVisitFilter.isPrivateLocation(lat, lon, placeRepository.all()) }) }

    val cloudLlm: CloudLlmClient by lazy { CloudLlmClient(settingsRepository.secureApiKey) }

    private val validatedReportClient by lazy { ValidatedReportClient(cloudLlm) }

    val reportGenerator: ReportGenerator by lazy {
        ReportGenerator(
            appContext = this,
            settingsRepository = settingsRepository,
            validatedReportClient = validatedReportClient,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
            placeRepository = placeRepository,
        )
    }

    val pulseGenerator: PulseReportGenerator by lazy {
        PulseReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
            placeRepository = placeRepository,
        )
    }

    val weeklyGenerator: WeeklyReportGenerator by lazy {
        WeeklyReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
            placeRepository = placeRepository,
        )
    }

    val diaryShareService by lazy {
        com.dailybeat.app.export.DiaryShareService(settingsRepository, visitRepository,
            eventRepository, placeRepository, diaryRepository, db)
    }

    val packageExporter: PackageExporter by lazy {
        PackageExporter(this, diaryShareService, pdfExporter)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        DailyReminderScheduler.createChannel(this)
        DailyReminderScheduler.scheduleNext(this)
        // The 1 PM midday pulse was removed. Force the setting off and cancel any alarm a
        // previous version may have scheduled, so it can never fire again.
        settingsRepository.setAutoMiddayPulse(false)
        PulseScheduler.cancel(this)
        HistoryRetentionWorker.applySchedule(this, settingsRepository.get().historyRetentionDays)
        com.dailybeat.app.capture.CaptureRecoveryWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                com.dailybeat.app.capture.LocationService.CHANNEL_ID,
                getString(R.string.location_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.location_channel_description)
                // The foreground-service notification must remain in the shade while capture is
                // active, but it is a status indicator—not an unread item. Counting it on the
                // launcher icon made DailyBeat appear to have a mystery notification forever.
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DailyReminderScheduler.CHANNEL_ID,
                "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
