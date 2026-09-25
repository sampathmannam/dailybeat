package com.dailybeat.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.settings.JournalProfile
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regressions from the physical-phone audit; only synthetic, disposable QA history is touched. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PhoneAuditRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: DailyBeatApp

    @Before fun prepare() {
        requireDisposableTestApp()
        val automation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setGpsEnabled(false)
        app.settingsRepository.setCloudLlmEnabled(false)
        app.settingsRepository.setGeocodingEndpoint("")
        app.settingsRepository.setJournalProfile(JournalProfile.POLICE)
        app.mapSettings.setOnline(false)
        runBlocking(Dispatchers.IO) { app.db.clearAllTables() }
        grantCorePermissions()
        compose.activityRule.scenario.recreate()
    }

    @Test fun todayAndDaysCountObservedTimeNotTheOvernightGap() {
        val start = DayBounds.dayStartEnd(DateKeys.today()).first
        val minutes = listOf(9 * 60, 9 * 60 + 5, 20 * 60, 20 * 60 + 5)
        runBlocking(Dispatchers.IO) {
            minutes.forEachIndexed { index, minute ->
                app.db.breadcrumbs().insert(LocationBreadcrumb(timestampMs = start + minute * 60_000L,
                    latitude = 11.4557 + index * 0.001, longitude = 78.1856, accuracyM = 5f))
                app.db.events().insert(Event(timestamp = start + minute * 60_000L, type = "VISIT",
                    rawText = "QA automatic stop $index"))
            }
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("today_list").performScrollToNode(hasTestTag("today_summary"))
        compose.waitUntilAtLeastOneExists(hasText("10m"), 10_000)
        compose.onNodeWithText("10m").assertIsDisplayed()
        compose.onNodeWithTag("nav_days").performClick()
        compose.onNodeWithTag("feed_list").performScrollToNode(hasText("Tracked time"))
        compose.onNodeWithText("10m").assertIsDisplayed()
        compose.onNodeWithText("11h 5m").assertDoesNotExist()
        compose.onNodeWithTag("nav_today").performClick()
        compose.onNodeWithTag("today_list").performScrollToNode(hasTestTag("review_day"))
        compose.onNodeWithTag("review_day").performClick()
        compose.onNodeWithTag("review_day_screen").performScrollToNode(hasText("Events"))
        compose.onNodeWithText("Events").assertIsDisplayed()
        compose.onNodeWithText("Notes").assertDoesNotExist()
    }

    @Test fun settingsKeyboardDoesNotPanHeaderUnderStatusBar() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_category_journal").performClick()
        val field = hasText(app.getString(R.string.supervisor_name_label)) and hasSetTextAction()
        compose.onNodeWithTag("settings_list").performScrollToNode(field)
        compose.onNode(field).performClick().performTextInput("QA supervisor")
        compose.waitUntil(10_000) { keyboardVisible() }
        // IME visibility flips at animation start; wait for resize/bring-into-view to finish.
        compose.waitUntil(5_000) { compose.onNode(field).isDisplayed() }
        val back = compose.onNodeWithTag("settings_back").fetchSemanticsNode().boundsInWindow
        var statusBottom = 0
        compose.activityRule.scenario.onActivity { activity ->
            statusBottom = ViewCompat.getRootWindowInsets(activity.window.decorView)!!
                .getInsets(WindowInsetsCompat.Type.statusBars()).top
        }
        assertTrue("Header overlaps the status bar: $back / $statusBottom", back.top >= statusBottom)
        compose.onNode(field).assertIsDisplayed()
        assertEquals("QA supervisor", app.settingsRepository.get().supervisorName)
        saveEvidence("settings-keyboard")
    }

    @Test fun noteCanBeSavedWithTheKeyboardOpenWithoutDraggingTheSheet() {
        compose.onNodeWithTag("today_list").performScrollToNode(hasTestTag("add_moment"))
        compose.onNodeWithTag("add_moment").performClick()
        compose.onNodeWithTag("moment_note").performClick().performTextInput("QA keyboard regression note")
        // The dialog hosts its own IME insets. Wait for it through the focused window, not
        // the Activity's decor; the save must actually reach the database with IME open.
        compose.waitUntil(10_000) {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }
        compose.onNodeWithText("Save note").performScrollTo().assertIsDisplayed()
        saveEvidence("note-keyboard")
        compose.onNodeWithText("Save note").performClick()
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) { app.db.events().all().any { it.rawText == "QA keyboard regression note" } }
        }
        compose.onNodeWithTag("moment_sheet_content").assertDoesNotExist()
    }

    @Test fun patternPrivacyCopyDistinguishesLocalAnalysisFromOptionalCloudReports() {
        val text = app.getString(R.string.today_pattern_privacy)
        compose.onNodeWithTag("today_list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
        assertTrue(text.contains("Optional Cloud AI reports"))
    }

    private fun saveEvidence(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val directory = java.io.File(app.filesDir, "ui-evidence").apply { mkdirs() }
        java.io.File(directory, "$name.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        compose.activityRule.scenario.onActivity {
            visible = ViewCompat.getRootWindowInsets(it.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }
}
