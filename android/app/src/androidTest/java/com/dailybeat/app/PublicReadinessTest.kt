package com.dailybeat.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.backup.BackupEnvelope
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.settings.JournalProfile
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PublicReadinessTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<DailyBeatApp>()
    @Before fun prepare() {
        requireDisposableTestApp()
        app.settingsRepository.setGpsEnabled(false)
        com.dailybeat.app.capture.CaptureController.applyFromSettings(app)
        runBlocking { withContext(Dispatchers.IO) { app.db.clearAllTables() } }
        app.settingsRepository.setJournalProfile(JournalProfile.PERSONAL)
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setCloudLlmEnabled(false)
        app.settingsRepository.setAutoEveningReport(false)
        composeRule.activityRule.scenario.recreate()
    }
    @Test fun offlineNotesAreSearchableWithoutACloudAccount() {
        runBlocking { app.eventRepository.addManualEvent("Warranty follow-up for the generator") }
        composeRule.onNodeWithTag("nav_days").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("history_search"), 15000)
        composeRule.onNodeWithTag("history_search").performTextReplacement("Warranty")
        composeRule.waitUntilAtLeastOneExists(hasText("Warranty follow-up for the generator"), 15000)
        composeRule.onNodeWithText("Warranty follow-up for the generator").assertIsDisplayed()
    }
    @Test fun nativePdfZipUsesReviewedCopyAndExcludesDiagnosticLogs() = runBlocking {
        val date = DateKeys.today()
        app.diaryRepository.saveForDate(date, "Reviewed field-work note")
        val preview = app.diaryShareService.prepare(date, "Reviewed field-work note")
        val zip = withContext(Dispatchers.IO) { app.packageExporter.exportWeekPackage(listOf(preview)) }
        try {
            ZipFile(zip).use { archive ->
                assertEquals(listOf("diaries/$date.txt", "diaries/$date.pdf"),
                    archive.entries().asSequence().map { it.name }.toList())
                assertEquals(preview.text, archive.getInputStream(archive.getEntry("diaries/$date.txt")).bufferedReader().readText())
                val pdf = archive.getInputStream(archive.getEntry("diaries/$date.pdf")).readBytes()
                assertTrue(String(pdf.take(4).toByteArray()) == "%PDF")
                assertTrue(pdf.size > 100)
            }
        } finally { zip.delete() }
    }
    @Test fun platformCryptoCanRecoverAndRejectsWrongPassphrase() {
        val secret = "harbour comet velvet cedar orbit lantern"
        val encrypted = BackupEnvelope.seal("Synthetic journal", secret.toCharArray())
        assertEquals("Synthetic journal", BackupEnvelope.open(encrypted, secret.toCharArray()))
        assertTrue(runCatching {
            BackupEnvelope.open(encrypted, "different recovery words altogether now".toCharArray())
        }.isFailure)
    }

    @Test fun diaryShareRequiresAnExplicitReviewOfTheOutgoingText() {
        runBlocking { app.diaryRepository.saveForDate(DateKeys.today(), "Exact sharing copy for QA") }
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), 15000)
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open diary"))
        composeRule.onNodeWithText("Open diary").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("diary_editor"), 15000)
        composeRule.onNodeWithTag("diary_list").performScrollToNode(hasText("Share PDF"))
        composeRule.onNodeWithText("Share PDF").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("share_preview"), 15000)
        composeRule.onNodeWithText("Review sharing copy").assertIsDisplayed()
        composeRule.onNodeWithText("Choose sharing app").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("share_preview").assertDoesNotExist()
    }
}
