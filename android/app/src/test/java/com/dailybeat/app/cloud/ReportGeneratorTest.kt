package com.dailybeat.app.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReportGeneratorTest {

    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var diaries: DiaryRepository
    private lateinit var events: EventRepository
    private lateinit var generator: ReportGenerator

    private class FakeCloudLlm(context: Context, private val reply: String) :
        CloudLlmClient(InMemoryApiKeyStore(context)) {
        override suspend fun generate(
            settings: AppSettings,
            systemPrompt: String,
            userPrompt: String,
        ): Result<String> = Result.success(reply)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context, InMemoryApiKeyStore(context))
        settings.setCloudLlmEnabled(true)
        diaries = DiaryRepository(db.diaries())
        events = EventRepository(db.events())
        generator = ReportGenerator(
            settingsRepository = settings,
            cloudLlm = FakeCloudLlm(context, "Generated AI report body."),
            visitRepository = VisitRepository(db.visits()),
            eventRepository = events,
            diaryRepository = diaries,
            appContext = context,
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `unattended report keeps what the officer wrote by hand`() = runBlocking {
        val today = DateKeys.today()
        events.addManualEvent("Court attendance")
        diaries.saveForDate(today, "Hand-written diary the officer typed during the day.")

        generator.generateUnattendedForDate(today).getOrThrow()

        val stored = diaries.textForDate(today).orEmpty()
        assertTrue(
            "The 8 PM auto-report destroyed the officer's own diary text. Stored: $stored",
            stored.contains("Hand-written diary the officer typed during the day."),
        )
        assertTrue("The auto-report was not saved. Stored: $stored", stored.contains("Generated AI report body."))
    }

    @Test
    fun `repeated unattended runs do not stack duplicate reports`() = runBlocking {
        val today = DateKeys.today()
        events.addManualEvent("Court attendance")
        diaries.saveForDate(today, "Officer notes.")

        generator.generateUnattendedForDate(today).getOrThrow()
        generator.generateUnattendedForDate(today).getOrThrow()

        val stored = diaries.textForDate(today).orEmpty()
        assertEquals(
            "A retry must replace the previous auto-report, not append another copy.",
            1,
            Regex("Generated AI report body\\.").findAll(stored).count(),
        )
        assertTrue(stored.contains("Officer notes."))
    }

    @Test
    fun `unattended report is saved when the day has no diary yet`() = runBlocking {
        val today = DateKeys.today()
        events.addManualEvent("Court attendance")

        generator.generateUnattendedForDate(today).getOrThrow()

        assertTrue(diaries.textForDate(today).orEmpty().contains("Generated AI report body."))
    }

    @Test
    fun `explicit generation replaces the diary because the officer asked for it`() = runBlocking {
        val today = DateKeys.today()
        events.addManualEvent("Court attendance")
        diaries.saveForDate(today, "Draft to be replaced.")

        generator.generateAndSaveForDate(today).getOrThrow()

        assertEquals("Generated AI report body.", diaries.textForDate(today))
    }

    @Test
    fun `report fails clearly when the day has no captured activity`() = runBlocking {
        val result = generator.generateForDate(DateKeys.today())

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("No passive data"),
        )
    }
}
