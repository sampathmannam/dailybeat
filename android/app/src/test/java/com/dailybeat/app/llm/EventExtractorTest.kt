package com.dailybeat.app.llm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.cloud.CloudTextGenerator
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventExtractorTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(context, InMemoryApiKeyStore(context))
        settings.setCloudLlmEnabled(true)
    }

    @Test
    fun `cloud metadata never replaces the officer's transcript`() = runBlocking {
        val transcript = "Met Inspector Rao at 14:10 and reviewed case 42 in full detail."
        val extractor = EventExtractor(
            cloudLlm = fakeCloud(
                """{"timestamp_guess":"14:10","place_guess":"HQ","people":["Rao"],"case_numbers":["42"],"summary":"Shortened by model"}""",
            ),
            settingsRepository = settings,
        )

        val event = extractor.extract(transcript).getOrThrow()

        assertEquals(transcript, event.rawText)
        assertEquals("HQ", event.placeName)
        assertEquals("Rao", event.peopleMentioned)
        assertEquals("42", event.caseNumbers)
    }

    @Test
    fun `malformed cloud JSON is reported as failure`() = runBlocking {
        val extractor = EventExtractor(fakeCloud("not-json"), settings)

        val result = extractor.extract("Briefing completed.")

        assertTrue(result.isFailure)
    }

    private fun fakeCloud(reply: String) = object : CloudTextGenerator {
        override suspend fun generate(
            settings: AppSettings,
            systemPrompt: String,
            userPrompt: String,
            maxOutputTokens: Int,
        ): Result<String> = Result.success(reply)
    }
}
