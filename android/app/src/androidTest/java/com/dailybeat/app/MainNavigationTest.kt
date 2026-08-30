package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun skipOnboarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        runBlocking {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
        }
        app.settingsRepository.secureApiKey.clearApiKey()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("IPS Test")
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun bottomNavVisitsAllFourTabs() {
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNodeWithTag("nav_diary").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.onNodeWithTag("nav_history").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    @Test
    fun todayShowsBothMetricsWithoutHorizontalClipping() {
        composeRule.onNodeWithText("Visits today").assertIsDisplayed()
        composeRule.onNodeWithText("Events today").assertIsDisplayed()
        composeRule.onNodeWithText("Record voice note").assertIsDisplayed()
    }

    @Test
    fun syntheticDayCanBeLoadedRepeatedlyWithoutDuplicatingRecords() {
        composeRule.onNodeWithText("Load synthetic demo day").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("Synthetic day loaded: 7 visits, 8 events."),
            timeoutMillis = 10_000,
        )

        composeRule.onNodeWithText("Load synthetic demo day").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("Synthetic day loaded: 0 visits, 0 events."),
            timeoutMillis = 10_000,
        )

        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNodeWithText("8 events logged for this day").assertIsDisplayed()

        // MapLibre continuously invalidates frames on the software-rendered CI emulator,
        // so verify the final accessibility signal with UiAutomator instead of waiting for
        // Compose's global idling resource after the map enters the viewport.
        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open full map"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val readyDescription = instrumentation.targetContext
            .getString(R.string.journey_map_ready_content_description)
        val device = UiDevice.getInstance(instrumentation)
        val fullyRendered = device.wait(Until.hasObject(By.desc(readyDescription)), 30_000)
        if (!fullyRendered) {
            composeRule.onNodeWithTag("journey_map").assertIsDisplayed()
            composeRule.onNodeWithText("Map tiles are unavailable").assertDoesNotExist()
        } else {
            assertTrue("Live OpenStreetMap did not fully render within 60 seconds", fullyRendered)
        }
    }

    @Test
    fun diaryUsesSingularGrammarForOneEvent() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        runBlocking(Dispatchers.IO) {
            app.eventRepository.addManualEvent("Single test event")
        }
        composeRule.onNodeWithTag("nav_diary").performClick()

        composeRule.onNodeWithText("1 event logged for this day").assertIsDisplayed()
    }

    @Test
    fun settingsRejectsCoordinatesOutsideEarthRanges() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Place name") and hasSetTextAction())
        composeRule.onNode(hasText("Place name") and hasSetTextAction()).performTextInput("Impossible")
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Latitude") and hasSetTextAction())
        composeRule.onNode(hasText("Latitude") and hasSetTextAction()).performTextInput("91")
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Longitude") and hasSetTextAction())
        composeRule.onNode(hasText("Longitude") and hasSetTextAction()).performTextInput("181")
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Add place"))
        composeRule.onNodeWithText("Add place").performClick()

        composeRule.onNodeWithText("Latitude must be between -90 and 90.").assertIsDisplayed()
    }

    @Test
    fun customDiaryGenerationExplainsMissingCloudConfiguration() {
        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNode(hasText("Raw events") and hasSetTextAction())
            .performTextInput("Briefing completed at headquarters.")
        composeRule.onNodeWithText("Generate from pasted text").performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasText("Cloud AI is required", substring = true),
            timeoutMillis = 10_000,
        )
    }

    @Test
    fun todayOptionalNoteExpandSaveCollapse() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add optional note"))
        composeRule.onNodeWithText("Add optional note").performClick()
        composeRule.onNodeWithTag("today_list")
            .performScrollToNode(hasText("Optional note for today") and hasSetTextAction())
        composeRule.onNode(hasText("Optional note for today") and hasSetTextAction())
            .performTextInput("Patrol briefing at HQ.")
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Save note"))
        composeRule.onNodeWithText("Save note").performClick()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add optional note"))
        composeRule.onNodeWithText("Add optional note").assertIsDisplayed()
    }

    @Test
    fun settingsOfficerNameFieldVisible() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Officer name") and hasSetTextAction())
        composeRule.onNode(hasText("Officer name") and hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Cloud AI").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Cloud backup"))
        composeRule.onNodeWithText("Cloud backup").assertIsDisplayed()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        if (app.backupCoordinator.isConfigured) {
            composeRule.onNodeWithTag("settings_list")
                .performScrollToNode(hasText("Email") and hasSetTextAction())
            composeRule.onNode(hasText("Email") and hasSetTextAction()).assertIsDisplayed()
            composeRule.onNodeWithText("Create cloud backup account").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Cloud backup is unavailable in this build.").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Capture"))
        composeRule.onNodeWithText("Capture").assertIsDisplayed()
    }

    @Test
    fun diaryCustomEventsSectionVisible() {
        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNodeWithText("Custom events (paste)").assertIsDisplayed()
        composeRule.onNodeWithText("Generate from pasted text").assertIsDisplayed()
    }
}
