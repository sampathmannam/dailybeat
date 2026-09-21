package com.dailybeat.app.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.*
import com.dailybeat.app.data.repo.*
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiaryShareServiceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: DailyBeatDb
    private lateinit var service: DiaryShareService
    private lateinit var diaries: DiaryRepository
    private val date = LocalDate.of(2026, 9, 15)
    private val time = date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Before fun before() {
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java).build()
        diaries = DiaryRepository(db.diaries())
        service = DiaryShareService(SettingsRepository(context), VisitRepository(db.visits()),
            EventRepository(db.events()), PlaceRepository(db.places()), diaries, db)
    }
    @After fun after() { db.close() }
    @Test fun privacyChangeAfterOldDraftRebuildsSharingCopyWithoutMutatingDiary() = runBlocking {
        diaries.saveForDate(date, "Old prose: met someone at the secret address.")
        db.places().insert(Place(name = "Secret address", latitude = 10.0, longitude = 20.0, isPrivate = true))
        db.visits().insert(LocationVisit(startMs = time, endMs = time + 60000, latitude = 10.0, longitude = 20.0, placeName = "Secret address"))
        db.events().insert(Event(timestamp = time + 1000, type = "manual", rawText = "Sensitive note during private visit"))
        db.events().insert(Event(timestamp = time + 120000, type = "manual", rawText = "Public follow-up"))
        val preview = service.prepare(date, diaries.textForDate(date)!!)
        assertFalse(preview.text.contains("secret", true))
        assertFalse(preview.text.contains("Sensitive"))
        assertTrue(preview.text.contains("Public follow-up"))
        assertTrue(diaries.textForDate(date)!!.contains("secret"))
        service.requireCurrent(preview)
    }
    @Test fun changingPrivacyOrSourcesInvalidatesPreviouslyReviewedCopy() = runBlocking {
        diaries.saveForDate(date, "Public diary")
        val preview = service.prepare(date, "Public diary")
        db.places().insert(Place(name = "Home", latitude = 1.0, longitude = 2.0, isPrivate = true))
        assertTrue(runCatching { service.requireCurrent(preview) }.isFailure)
        val refreshed = service.prepare(date, "Public diary")
        db.events().insert(Event(timestamp = time, type = "manual", rawText = "New note"))
        assertTrue(runCatching { service.requireCurrent(refreshed) }.isFailure)
    }
    @Test fun stalePreviewCannotBeExported() = runBlocking {
        diaries.saveForDate(date, "Original")
        val preview = service.prepare(date, "Original")
        diaries.saveForDate(date, "Changed")
        assertTrue(runCatching {
            PackageExporter(context, service, PdfExporter(context)).exportWeekPackage(listOf(preview))
        }.isFailure)
    }

    @Test fun replacingDataInvalidatesPreviewEvenWhenRestoredRecordsAreIdentical() = runBlocking {
        diaries.saveForDate(date, "Original")
        val preview = service.prepare(date, "Original")
        com.dailybeat.app.capture.CaptureStorageGate.invalidatePersonalData()
        assertTrue(runCatching { service.requireCurrent(preview) }.isFailure)
    }
}
