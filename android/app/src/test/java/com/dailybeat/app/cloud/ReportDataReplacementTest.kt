package com.dailybeat.app.cloud

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReportDataReplacementTest {
    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var diaries: DiaryRepository
    private lateinit var events: EventRepository
    private lateinit var delayed: DelayedCloud

    private class DelayedCloud(context: Context) : CloudLlmClient(InMemoryApiKeyStore(context)) {
        val requested = CompletableDeferred<Unit>()
        val complete = CompletableDeferred<Unit>()
        override suspend fun generate(
            settings: AppSettings, systemPrompt: String, userPrompt: String, maxOutputTokens: Int,
        ): Result<String> {
            requested.complete(Unit)
            complete.await()
            return Result.success("Recorded private activity [E1].")
        }
    }

    @Before fun before() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context, InMemoryApiKeyStore(context)).apply { setCloudLlmEnabled(true) }
        diaries = DiaryRepository(db.diaries())
        events = EventRepository(db.events())
        delayed = DelayedCloud(context)
    }

    @After fun after() { db.close() }

    private fun daily() = ReportGenerator(context, settings, ValidatedReportClient(delayed),
        VisitRepository(db.visits()), events, diaries, PlaceRepository(db.places()))

    private fun assertLateReplyCannotPersist(
        replacement: String? = null,
        generate: suspend () -> Result<String>,
    ) = runBlocking {
        withTimeout(20_000) {
            events.addManualEvent("Private source note")
            diaries.saveToday("Private old diary")
            val result = async { generate() }
            delayed.requested.await()
            CaptureStorageGate.mutex.withLock {
                CaptureStorageGate.invalidatePersonalData()
                withContext(Dispatchers.IO) { db.clearAllTables() }
                if (replacement != null) diaries.saveToday(replacement)
            }
            delayed.complete.complete(Unit)
            assertTrue(result.await().isFailure)
            assertEquals(replacement, diaries.todayText())
            assertTrue(db.diaries().allRevisions().isEmpty())
        }
    }

    @Test fun lateExplicitReportCannotRecreateErasedDiary() =
        assertLateReplyCannotPersist { daily().generateAndSaveForDate(DateKeys.today()) }

    @Test fun lateUnattendedReportCannotRecreateErasedDiary() =
        assertLateReplyCannotPersist { daily().generateUnattendedForDate(DateKeys.today()) }

    @Test fun lateWeeklyReportCannotRecreateErasedDiary() = assertLateReplyCannotPersist {
        WeeklyReportGenerator(settings, delayed, VisitRepository(db.visits()), events, diaries,
            PlaceRepository(db.places())).generateAndSave()
    }

    @Test fun latePulseCannotRecreateErasedDiary() = assertLateReplyCannotPersist {
        PulseReportGenerator(settings, delayed, VisitRepository(db.visits()), events, diaries,
            PlaceRepository(db.places())).generateAndSavePulse()
    }

    @Test fun lateReportCannotOverwriteRestoredDiary() = assertLateReplyCannotPersist("Restored diary") {
        daily().generateAndSaveForDate(DateKeys.today())
    }
}
