package com.dailybeat.app

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.backup.BackupSnapshotCodec
import com.dailybeat.app.backup.LocalBackupStore
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DailyBeatReliabilityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: DailyBeatApp

    @Before fun resetDisposableApp() {
        requireDisposableTestApp()
        app = ApplicationProvider.getApplicationContext()
        LocationService.stop(app)
        app.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        app.settingsRepository.secureApiKey.clearApiKey()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("Synthetic reviewer")
        app.settingsRepository.setGpsEnabled(false)
        app.settingsRepository.setAutoEveningReport(false)
        app.settingsRepository.setAutoMiddayPulse(false)
        runBlocking(Dispatchers.IO) { app.db.clearAllTables() }
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test fun manualDiaryCanBeClearedRewrittenAndRestoredAfterRecreation() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add diary"))
        composeRule.onNodeWithText("Add diary").performClick()
        composeRule.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_editor"))
        composeRule.onNodeWithTag("diary_editor").performTextReplacement("Synthetic manual diary")
        waitForDiary("Synthetic manual diary")
        composeRule.onNodeWithTag("diary_editor").performTextReplacement("")
        waitForDiary("")
        composeRule.onNodeWithTag("diary_editor").assertExists().performTextInput("Replacement தமிழில் 🙂")
        waitForDiary("Replacement தமிழில் 🙂")
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("diary_list").performScrollToNode(hasTestTag("diary_editor"))
        composeRule.onNodeWithTag("diary_editor").assertTextContains("Replacement தமிழில் 🙂")
    }

    @Test fun nativePdfAndWeekZipContainOnlyThisWeeksSavedDiaries() = runBlocking {
        val today = DateKeys.today()
        withContext(Dispatchers.IO) {
            app.diaryRepository.saveForDate(today, "Synthetic current diary\n".repeat(150))
            app.diaryRepository.saveForDate(today.minusDays(6), "Synthetic first day")
            app.diaryRepository.saveForDate(today.minusDays(7), "Older diary must stay private")
            app.diaryRepository.saveForDate(today.plusDays(1), "Future diary must stay private")
            val before = app.db.diaries().all()
            val pdf = app.pdfExporter.exportDairy("Synthetic reviewer", before.first { it.dateKey == today.toString() }.text, today, "Synthetic supervisor")
            PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount > 1)
            }
            val zip = app.packageExporter.exportWeekPackage("Synthetic reviewer", "Synthetic supervisor")
            ZipFile(zip).use { archive ->
                assertNotNull(archive.getEntry("diaries/$today.txt"))
                assertNotNull(archive.getEntry("diaries/${today.minusDays(6)}.pdf"))
                assertNull(archive.getEntry("diaries/${today.minusDays(7)}.txt"))
                assertNull(archive.getEntry("diaries/${today.plusDays(1)}.txt"))
                assertEquals(before.first { it.dateKey == today.toString() }.text,
                    archive.getInputStream(archive.getEntry("diaries/$today.txt")).bufferedReader().use { it.readText() })
            }
            assertEquals(before, app.db.diaries().all())
        }
    }

    @Test fun localBackupRoundTripRestoresDiaryNotesPlacesAndVisitsWithoutApiKey() = runBlocking {
        withContext(Dispatchers.IO) {
            app.eventRepository.addManualEvent("Synthetic backup note")
            app.diaryRepository.saveToday("Synthetic backup diary")
            app.placeRepository.add("Synthetic station", 11.4557, 78.1856, 150)
            seedVisit()
            app.settingsRepository.secureApiKey.setApiKey("synthetic-secret-never-export")
            val store = LocalBackupStore(app.db, app.settingsRepository)
            val before = store.createSnapshot()
            val encoded = BackupSnapshotCodec.encode(before)
            assertFalse(encoded.contains("synthetic-secret-never-export"))
            val decoded = BackupSnapshotCodec.decode(encoded)
            app.db.clearAllTables()
            store.restore(decoded)
            val after = store.createSnapshot()
            assertEquals(before.events, after.events)
            assertEquals(before.diaries, after.diaries)
            assertEquals(before.places, after.places)
            assertEquals(before.visits, after.visits)
            assertEquals(before.settings, after.settings)
            app.settingsRepository.secureApiKey.clearApiKey()
        }
    }

    @Test fun fullMapReturnsToTodayAndDoesNotRestoreRemovedDsrRoute() {
        runBlocking(Dispatchers.IO) { seedVisit() }
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open full map"))
        composeRule.onNodeWithTag("today_journey_map").assertIsDisplayed()
        composeRule.onNodeWithText("Open full map").performClick()
        composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("journey_map_back").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dsr").assertDoesNotExist()
    }

    @Test fun todayStatusChipsUseTheSameTopEdgeAndHeight() {
        val gpsBounds = composeRule.onNodeWithTag("status_gps")
            .fetchSemanticsNode().boundsInRoot
        val cloudBounds = composeRule.onNodeWithTag("status_cloud")
            .fetchSemanticsNode().boundsInRoot

        assertEquals(gpsBounds.top, cloudBounds.top, 0.5f)
        assertEquals(gpsBounds.height, cloudBounds.height, 0.5f)
    }

    private fun waitForDiary(text: String) {
        composeRule.waitUntil(10_000) { runBlocking { app.diaryRepository.todayText() == text } }
    }

    private suspend fun seedVisit() {
        val start = DayBounds.dayStartEnd(DateKeys.today()).first
        app.visitRepository.insert(LocationVisit(startMs = start, endMs = start + 600_000,
            latitude = 11.4557, longitude = 78.1856, placeName = "Synthetic station"))
    }
}
