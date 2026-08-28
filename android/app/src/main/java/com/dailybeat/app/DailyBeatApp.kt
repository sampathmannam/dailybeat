package com.dailybeat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.dailybeat.app.capture.CallLogWorker
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.capture.VoiceCaptureService
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.export.PdfExporter
import com.dailybeat.app.llm.DairyGenerator
import com.dailybeat.app.llm.EventExtractor
import com.dailybeat.app.llm.LlmEngine
import com.dailybeat.app.util.ModelImporter

class DailyBeatApp : Application() {

    val db: DailyBeatDb by lazy {
        Room.databaseBuilder(this, DailyBeatDb::class.java, "dailybeat.db").build()
    }

    val llm: LlmEngine by lazy { LlmEngine(this) }

    val dairyGenerator: DairyGenerator by lazy { DairyGenerator(llm, db) }

    val eventExtractor: EventExtractor by lazy { EventExtractor(llm) }

    val eventRepository: EventRepository by lazy { EventRepository(db.events()) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val pdfExporter: PdfExporter by lazy { PdfExporter(this) }

    val modelImporter: ModelImporter by lazy { ModelImporter(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        CallLogWorker.schedule(this)
        applyCaptureSettings()
        modelImporter.importFromDownloads()
    }

    fun applyCaptureSettings() {
        val settings = settingsRepository.get()
        if (settings.gpsCaptureEnabled) {
            LocationService.start(this)
        } else {
            LocationService.stop(this)
        }
        VoiceCaptureService.start(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                VoiceCaptureService.CHANNEL_ID,
                "Voice capture",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LocationService.CHANNEL_ID,
                "Location capture",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
