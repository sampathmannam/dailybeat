package com.dailybeat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.dailybeat.app.cloud.CloudLlmClient
import com.dailybeat.app.cloud.PulseReportGenerator
import com.dailybeat.app.cloud.ReportGenerator
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.MIGRATION_2_3
import com.dailybeat.app.data.db.MIGRATION_3_4
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.export.PdfExporter
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.llm.DairyGenerator
import com.dailybeat.app.llm.EventExtractor
import com.dailybeat.app.llm.LlmEngine
import com.dailybeat.app.notify.DailyReminderScheduler
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.util.ModelImporter

class DailyBeatApp : Application() {

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db")
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    val llm: LlmEngine by lazy { LlmEngine(this) }

    val dairyGenerator: DairyGenerator by lazy { DairyGenerator(llm, db) }

    val eventExtractor: EventExtractor by lazy { EventExtractor(llm) }

    val eventRepository: EventRepository by lazy { EventRepository(db.events()) }

    val diaryRepository: DiaryRepository by lazy { DiaryRepository(db.diaries()) }

    val placeRepository: PlaceRepository by lazy { PlaceRepository(db.places()) }

    val visitRepository: VisitRepository by lazy { VisitRepository(db.visits()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val pdfExporter: PdfExporter by lazy { PdfExporter(this) }

    val modelImporter: ModelImporter by lazy { ModelImporter(this) }

    val osmGeocoder: OsmGeocoder by lazy { OsmGeocoder(db.geocodes()) }

    val cloudLlm: CloudLlmClient by lazy { CloudLlmClient(settingsRepository.secureApiKey) }

    val reportGenerator: ReportGenerator by lazy {
        ReportGenerator(
            settingsRepository = settingsRepository,
            cloudLlm = cloudLlm,
            visitRepository = visitRepository,
            eventRepository = eventRepository,
            diaryRepository = diaryRepository,
            localGenerator = dairyGenerator,
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

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        DailyReminderScheduler.createChannel(this)
        DailyReminderScheduler.scheduleNext(this)
        PulseScheduler.scheduleNext(this)
        modelImporter.importFromDownloads()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                com.dailybeat.app.capture.LocationService.CHANNEL_ID,
                "Location capture",
                NotificationManager.IMPORTANCE_LOW,
            ),
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
