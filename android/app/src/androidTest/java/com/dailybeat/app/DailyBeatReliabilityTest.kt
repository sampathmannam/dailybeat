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
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.capture.CaptureHealth
import com.dailybeat.app.capture.LocationSample
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.capture.MotionState
import com.dailybeat.app.capture.MotionStateStore
import com.dailybeat.app.capture.SharedPreferencesVisitTrackerStateStore
import com.dailybeat.app.capture.VisitTrackerState
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.AppStorage
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
            val zip = app.packageExporter.exportWeekPackage(app.diaryShareService.prepareWeek())
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
            app.mapSettings.setOnline(true)
            val store = LocalBackupStore(app.db, app.settingsRepository)
            val before = store.createSnapshot()
            val encoded = BackupSnapshotCodec.encode(before)
            assertFalse(encoded.contains("synthetic-secret-never-export"))
            val decoded = BackupSnapshotCodec.decode(encoded)
            app.db.clearAllTables()
            app.mapSettings.setOnline(false)
            store.restore(decoded)
            assertFalse(app.mapSettings.state.value.allowOnlineMaps)
            assertFalse(com.dailybeat.app.maps.MapSettingsRepository(app).state.value.allowOnlineMaps)
            val after = store.createSnapshot()
            assertEquals(before.events, after.events)
            assertEquals(before.diaries, after.diaries)
            assertEquals(before.places, after.places)
            assertEquals(before.visits, after.visits)
            assertEquals(before.settings, after.settings)
            app.settingsRepository.secureApiKey.clearApiKey()
        }
    }

    @Test fun erasePhoneDataRemovesRecordsSecretsCheckpointsDiagnosticsAndExports() = runBlocking {
        withContext(Dispatchers.IO) {
            app.eventRepository.addManualEvent("Sensitive synthetic note")
            app.diaryRepository.saveToday("Sensitive synthetic diary")
            app.placeRepository.add("Sensitive synthetic place", 11.4557, 78.1856, 150)
            seedVisit()
            app.settingsRepository.secureApiKey.setApiKey("synthetic-secret-to-erase")
            app.captureHealthStore.accepted(
                LocationSample(11.4557, 78.1856, System.currentTimeMillis(), 10f),
                "good",
            )
            SharedPreferencesVisitTrackerStateStore(app).save(
                VisitTrackerState(
                    dwellLat = 11.4557,
                    dwellLon = 78.1856,
                    dwellStartMs = System.currentTimeMillis(),
                    lastSampleMs = System.currentTimeMillis(),
                    transitStartMs = 0,
                    transitLat = null,
                    transitLon = null,
                    departureLat = null,
                    departureLon = null,
                    inTransit = false,
                ),
            )
            MotionStateStore(app).record(MotionState.STILL, System.currentTimeMillis())
            CaptureAuditLog.log(app, "test", "Sensitive audit detail")
            OperationalFailureLog.record(app, "test", false, "Sensitive failure detail")
            AppStorage.outputFile(app, "synthetic-sensitive-export.txt")
                .writeText("Sensitive exported diary")

            app.mapSettings.setProvider(com.dailybeat.app.maps.MapProviderConfig(styleUrl = "https://maps.example/private-provider.json"))
            val mapStaging = java.io.File(app.noBackupFilesDir, "offline-maps/staging/test/partial").apply {
                parentFile!!.mkdirs(); writeText("partial map")
            }
            app.localDataEraser.erase()
            assertFalse(mapStaging.exists())
            assertFalse(app.mapSettings.state.value.allowOnlineMaps)
            assertEquals(com.dailybeat.app.maps.MapProviderConfig(), app.mapSettings.state.value.provider)

            assertTrue(app.db.events().all().isEmpty())
            assertTrue(app.db.diaries().all().isEmpty())
            assertTrue(app.db.places().all().isEmpty())
            assertTrue(app.db.visits().all().isEmpty())
            assertNull(app.settingsRepository.secureApiKey.getApiKey())
            assertEquals(CaptureHealth(), app.captureHealthStore.health.value)
            assertNull(SharedPreferencesVisitTrackerStateStore(app).load())
            assertEquals(MotionState.UNKNOWN, MotionStateStore(app).state)
            assertTrue(CaptureAuditLog.readRecent(app).isEmpty())
            assertTrue(OperationalFailureLog.readRecent(app).isEmpty())
            assertTrue(AppStorage.outputDir(app).listFiles().orEmpty().isEmpty())
            assertFalse(app.settingsRepository.get().gpsCaptureEnabled)
            assertFalse(app.settingsRepository.isOnboardingComplete())
        }
    }

    @Test fun fullMapReturnsToTodayAndDoesNotRestoreRemovedDsrRoute() {
        runBlocking(Dispatchers.IO) { seedVisit() }
        repeat(5) { iteration ->
            composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open full map"))
            composeRule.onNodeWithTag("today_journey_map").assertIsDisplayed()
            composeRule.onNodeWithText("Open full map").performClick()
            composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
            composeRule.runOnUiThread { app.mapSettings.setOnline(iteration % 2 == 0) }
            composeRule.activityRule.scenario.recreate()
            composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
            composeRule.onNodeWithTag("journey_map_back").performClick()
        }
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_dsr").assertDoesNotExist()
    }

    @Test fun todayKeepsDiagnosticsVisibleWithoutAMoreControl() {
        composeRule.onNodeWithTag("today_route_details").assertDoesNotExist()
        composeRule.onNodeWithTag("today_more").assertDoesNotExist()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasTestTag("capture_health"))
        composeRule.onNodeWithTag("capture_health").assertExists()
        composeRule.onNodeWithTag("status_gps").assertExists()
        composeRule.onNodeWithTag("status_cloud").assertExists()
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
