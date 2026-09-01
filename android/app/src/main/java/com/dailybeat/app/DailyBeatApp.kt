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
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.MIGRATION_2_3
import com.dailybeat.app.data.db.MIGRATION_3_4
import com.dailybeat.app.data.db.MIGRATION_4_5
import com.dailybeat.app.data.db.migration5To6
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

class DailyBeatApp : Application() {

    val patrolTrackCipher: PatrolTrackCipher by lazy { PatrolTrackCipher(this) }

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, migration5To6(patrolTrackCipher))
            .build()
    }

    val eventExtractor: EventExtractor by lazy { EventExtractor(cloudLlm, settingsRepository) }

    val eventRepository: EventRepository by lazy { EventRepository(db.events()) }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(db.diaries()) }

    val placeRepository: PlaceRepository by lazy { PlaceRepository(db.places()) }

    val visitRepository: VisitRepository by lazy { VisitRepository(db.visits()) }

    val patrolGridRepository: PatrolGridRepository by lazy {
        PatrolGridRepository(this, db.patrolTracks(), settingsRepository)
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
