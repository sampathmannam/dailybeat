package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.audit.CaptureAuditLog
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
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
        requireDisposableTestApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CaptureAuditLog.clear(context)
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
    fun bottomNavVisitsAllFourTabsWithoutDsr() {
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_dsr").assertDoesNotExist()
        composeRule.onNodeWithText("DSR Command").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_days").performClick()
        composeRule.onNodeWithTag("feed_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.onNodeWithTag("insights_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    @Test
    fun todayShowsBothMetricsWithoutHorizontalClipping() {
        composeRule.onNodeWithText("Distance").assertIsDisplayed()
        composeRule.onNodeWithText("Tracked").assertIsDisplayed()
        composeRule.onNodeWithText("Stops").assertIsDisplayed()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add moment"))
        composeRule.onNodeWithText("Add moment").assertIsDisplayed()
    }

    @Test
    fun syntheticDayCanBeLoadedRepeatedlyWithoutDuplicatingRecords() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Load synthetic demo day"))
        composeRule.onNodeWithText("Load synthetic demo day").performClick()
        waitForSyntheticAudit("Seeded 7 visits, 5 events")
        composeRule.onNodeWithTag("today_list")
            .performScrollToNode(hasText("Synthetic day loaded: 7 visits, 5 events."))
        composeRule.onNodeWithText("Synthetic day loaded: 7 visits, 5 events.").assertIsDisplayed()

        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Load synthetic demo day"))
        composeRule.onNodeWithText("Load synthetic demo day").performClick()
        waitForSyntheticAudit("Seeded 0 visits, 0 events")
        composeRule.onNodeWithTag("today_list")
            .performScrollToNode(hasText("Synthetic day loaded: 0 visits, 0 events."))
        composeRule.onNodeWithText("Synthetic day loaded: 0 visits, 0 events.").assertIsDisplayed()

        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add diary"))
        composeRule.onNodeWithText("Add diary").performClick()
        composeRule.onNodeWithText("5 events logged for this day").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Open full map"))
        composeRule.onNodeWithTag("journey_route_preview").assertIsDisplayed()
        composeRule.onNodeWithText("Open full map").performClick()
        composeRule.onNodeWithTag("journey_map_screen").assertIsDisplayed()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("journey_map_fallback", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("journey_map_ready", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("journey_map_back").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    @Test
    fun diaryUsesSingularGrammarForOneEvent() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        runBlocking(Dispatchers.IO) {
            app.eventRepository.addManualEvent("Single test event")
        }
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add diary"))
        composeRule.onNodeWithText("Add diary").performClick()

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
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add diary"))
        composeRule.onNodeWithText("Add diary").performClick()
        composeRule.onNode(hasText("Raw events") and hasSetTextAction())
            .performTextInput("Briefing completed at headquarters.")
        composeRule.onNodeWithText("Generate from pasted text").performClick()
        composeRule.onNodeWithTag("diary_list")
            .performScrollToNode(hasText("Cloud AI is required", substring = true))
        composeRule.onNodeWithText("Cloud AI is required", substring = true).assertIsDisplayed()
    }

    @Test
    fun todayOptionalNoteExpandSaveCollapse() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add moment"))
        composeRule.onNodeWithText("Add moment").performClick()
        composeRule.onNode(hasText("Optional note for today") and hasSetTextAction())
            .performTextInput("Patrol briefing at HQ.")
        composeRule.onNodeWithText("Save note").performClick()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add moment"))
        composeRule.onNodeWithText("Add moment").assertIsDisplayed()
    }

    @Test
    fun settingsOfficerNameFieldVisible() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Officer name") and hasSetTextAction())
        composeRule.onNode(hasText("Officer name") and hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Cloud AI"))
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
    fun savedCloudApiKeyCanBeRemovedWithConfirmation() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.secureApiKey.setApiKey("disposable-device-test-key")
        // The activity can create its Settings ViewModel before this direct fixture write.
        // Recreate it so the UI reads the encrypted store deterministically.
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("remove_api_key"))
        composeRule.onNodeWithTag("remove_api_key").performClick()
        composeRule.onNodeWithText("Remove saved API key?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_remove_api_key").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("API key removed from this phone", substring = true),
            timeoutMillis = 10_000,
        )

        assertFalse(app.settingsRepository.secureApiKey.hasApiKey())
        composeRule.onNodeWithText("Remove saved API key").assertDoesNotExist()
    }

    @Test
    fun deletingNamedPlaceRequiresConfirmation() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        runBlocking {
            withContext(Dispatchers.IO) {
                app.placeRepository.add("Disposable HQ", 11.4557, 78.1856)
            }
        }
        val place = runBlocking {
            withContext(Dispatchers.IO) { app.placeRepository.all().single() }
        }

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("settings_list"), timeoutMillis = 10_000)
        // Named places are below the fold on the physical phone. Ask the lazy list to compose
        // the target before asserting or tapping it.
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Disposable HQ"))
        composeRule.onNodeWithText("Disposable HQ").assertIsDisplayed()
        composeRule.onNodeWithTag("delete_place_${place.id}").performClick()
        composeRule.onNodeWithText("Delete named place?").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_delete_place").performClick()
        assertFalse(runBlocking { withContext(Dispatchers.IO) { app.placeRepository.all() } }.isEmpty())

        composeRule.onNodeWithTag("delete_place_${place.id}").performClick()
        composeRule.onNodeWithTag("confirm_delete_place").performClick()
        composeRule.waitUntil(10_000) {
            runBlocking { withContext(Dispatchers.IO) { app.placeRepository.all().isEmpty() } }
        }
        composeRule.onNodeWithText("Disposable HQ").assertDoesNotExist()
    }

    @Test
    fun diaryCustomEventsSectionVisible() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add diary"))
        composeRule.onNodeWithText("Add diary").performClick()
        composeRule.onNodeWithText("Custom events (paste)").assertIsDisplayed()
        composeRule.onNodeWithText("Generate from pasted text").assertIsDisplayed()
    }

    private fun waitForSyntheticAudit(fragment: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.waitUntil(10_000) {
            runBlocking { CaptureAuditLog.readRecent(context).any { fragment in it } }
        }
    }
}
