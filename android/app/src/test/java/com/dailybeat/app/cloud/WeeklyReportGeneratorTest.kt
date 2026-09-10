package com.dailybeat.app.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.data.repo.EventRepository
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.data.repo.VisitRepository
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import com.dailybeat.app.data.settings.SecureApiKeyStore
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
class WeeklyReportGeneratorTest {

    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var diaries: DiaryRepository
    private lateinit var generator: WeeklyReportGenerator

    /** Stands in for the network so the generator's save behaviour can be asserted. */
    private class FakeCloudLlm(context: Context, private val reply: String) :
        CloudLlmClient(SecureApiKeyStore(context)) {
        override suspend fun generate(
            settings: AppSettings,
            systemPrompt: String,
            userPrompt: String,
            maxOutputTokens: Int,
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
        generator = WeeklyReportGenerator(
            settingsRepository = settings,
            cloudLlm = FakeCloudLlm(context, "Weekly rollup body."),
            visitRepository = VisitRepository(db.visits()),
            eventRepository = EventRepository(db.events()),
            diaryRepository = diaries,
            placeRepository = PlaceRepository(db.places()),
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `weekly rollup keeps the official daily diary for the same day`() = runBlocking {
        val today = DateKeys.today()
        diaries.saveForDate(today, "Official daily diary for today.")

        generator.generateAndSave().getOrThrow()

        val stored = diaries.textForDate(today).orEmpty()
        assertTrue(
            "Weekly rollup destroyed the day's diary. Stored text was: $stored",
            stored.contains("Official daily diary for today."),
        )
        assertTrue("Weekly rollup was not saved. Stored text was: $stored", stored.contains("Weekly rollup body."))
    }

    @Test
    fun `weekly rollup is saved when the day has no diary yet`() = runBlocking {
        generator.generateAndSave().getOrThrow()

        val stored = diaries.textForDate(DateKeys.today()).orEmpty()
        assertTrue("Weekly rollup was not saved. Stored text was: $stored", stored.contains("Weekly rollup body."))
    }

    @Test
    fun `weekly rollup is not appended twice when regenerated`() = runBlocking {
        generator.generateAndSave().getOrThrow()
        generator.generateAndSave().getOrThrow()

        val stored = diaries.textForDate(DateKeys.today()).orEmpty()
        assertEquals(
            "Regenerating the rollup must replace the previous one, not stack copies.",
            1,
            Regex("Weekly rollup body\\.").findAll(stored).count(),
        )
    }
}
