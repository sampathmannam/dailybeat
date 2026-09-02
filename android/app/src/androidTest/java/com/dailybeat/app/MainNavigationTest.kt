package com.dailybeat.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.test.waitUntilDoesNotExist
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.lifecycle.ViewModelProvider
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.repo.PatrolGridRepository
import com.dailybeat.app.ui.patrol.PatrolGridViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MainNavigationTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun openPatrolRole() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("patrolgrid_missions", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("Inspector Rao")
        app.settingsRepository.setPatrolRole(PatrolRole.PATROL)
        app.settingsRepository.setActivePatrolMission(null)
        app.settingsRepository.setGpsEnabled(false)
        grantCorePermissions()
        // Start each test with a ViewModel created from the clean patrol-role
        // settings. Re-selecting the already configured role through the UI
        // emits a transient bottom snackbar that can physically cover the
        // bottom-most patrol controls on a slow emulator.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.viewModelStore.clear()
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("my_patrol_list"), 20_000)
    }

    @Test
    fun patrolBottomNavigationVisitsAvailableSections() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag("my_patrol_list"), 10_000)
        composeRule.onNodeWithTag("my_patrol_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_messages").assertDoesNotExist()

        composeRule.onNodeWithTag("nav_missions").performClick()
        composeRule.onNodeWithTag("missions_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Role preview").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_my_patrol").performClick()
        composeRule.onNodeWithTag("my_patrol_list").assertIsDisplayed()
    }

    @Test
    fun localRolePreviewSwitchesToSupervisorControl() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)
        composeRule.onNodeWithTag("patrol_control_list").assertIsDisplayed()
        composeRule.onNodeWithText("Patrol Control").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_alerts").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_units").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_missions").performClick()
        composeRule.onNodeWithTag("missions_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_units").performClick()
        composeRule.onNodeWithTag("units_list").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_control").performClick()
        composeRule.onNodeWithTag("patrol_control_list").assertIsDisplayed()
    }

    @Test
    fun patrolObservationRequiresDetail() {
        openActiveLocalPatrol()
        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("add_observation"))

        composeRule.onNodeWithTag("add_observation").performClick()
        composeRule.onNodeWithTag("field_update_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("save_field_update").assertIsNotEnabled()

        composeRule.onNodeWithTag("observation_detail")
            .performTextInput("Bus stand clear; street lighting operational.")
        composeRule.activityRule.scenario.onActivity { activity ->
            val keyboard = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            keyboard.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_field_update").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("field_update_dialog").assertDoesNotExist()
    }

    @Test
    fun patrolEndRequiresConfirmation() {
        openActiveLocalPatrol()

        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("end_patrol"))
        composeRule.onNodeWithTag("end_patrol").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("end_patrol_dialog").assertExists()
        composeRule.onNodeWithText("End this patrol?").assertIsDisplayed()
        composeRule.onNode(
            hasText("Patrol completed") and hasClickAction(),
        ).assertIsSelected()
        composeRule.onNode(
            hasText("Relieved by another unit") and hasClickAction(),
        ).performClick().assertIsSelected()
        composeRule.onNode(
            hasText("Mission cancelled") and hasClickAction(),
        ).performClick().assertIsSelected()
        composeRule.onNodeWithText("Continue patrol").performClick()
        composeRule.onNodeWithTag("end_patrol_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("end_patrol").assertIsDisplayed()

        composeRule.onNodeWithTag("end_patrol").performClick()
        composeRule.onNodeWithTag("confirm_end_patrol").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_closed_state"), 10_000)
        composeRule.onNodeWithTag("patrol_closed_state").assertIsDisplayed()
    }

    @Test
    fun activePatrolFieldActionsAndPriorityVisitWork() {
        openActiveLocalPatrol()

        composeRule.onNode(
            hasText("Bus stand") and hasClickAction(),
        ).performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Visited at 22:18"), 10_000)

        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("record_deviation"))
        composeRule.onNodeWithTag("record_deviation").performClick()
        composeRule.onNodeWithText("Record deviation").assertIsDisplayed()
        composeRule.onNodeWithTag("save_field_update").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("field_update_dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("record_deviation").performClick()
        composeRule.onNodeWithTag("deviation_detail")
            .performTextInput("Crowd blocked the suggested road; unit used the lit parallel lane.")
        composeRule.onNodeWithTag("save_field_update").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("field_update_dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("record_safety_event"))
        composeRule.onNodeWithTag("record_safety_event").performClick()
        composeRule.onNodeWithText("Record safety event").assertIsDisplayed()
        composeRule.onNodeWithTag("safety_detail")
            .performTextInput("Open drain reported near the east footpath; radio control informed.")
        composeRule.onNodeWithTag("save_field_update").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("field_update_dialog").assertDoesNotExist()
    }

    @Test
    fun patrolEndAllowsExplicitNonCompletionReason() {
        openActiveLocalPatrol()
        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("end_patrol"))
        composeRule.onNodeWithTag("end_patrol").assertIsDisplayed().performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("end_patrol_dialog"), 20_000)

        composeRule.onNode(
            hasText("Device issue") and hasClickAction(),
        ).performClick().assertIsSelected()
        composeRule.onNodeWithTag("confirm_end_patrol").performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_closed_state"), 10_000)
        composeRule.onNodeWithTag("patrol_closed_state").assertIsDisplayed()
    }

    @Test
    fun patrolMissionRowOpensMissionDetailSheet() {
        composeRule.onNodeWithTag("nav_missions").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("missions_list"), 10_000)

        composeRule.onNode(
            hasText("Night patrol · Sector 4") and hasClickAction(),
        ).performClick()

        composeRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("mission_detail_list").assertIsDisplayed()
        composeRule.onNodeWithText("Planned and recorded route").assertIsDisplayed()
    }

    @Test
    fun patrolSelectsAlternateAssignedMissionFromMissionDetails() {
        val missionTitle = "Day patrol · School corridor"
        val missionMatcher = hasText(missionTitle) and hasClickAction()
        composeRule.onNodeWithTag("nav_missions").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("missions_list"), 10_000)
        composeRule.onNodeWithTag("missions_list").performScrollToNode(missionMatcher)
        composeRule.onNode(missionMatcher).performClick()

        composeRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
        composeRule.onNode(
            hasText(missionTitle) and hasAnyAncestor(hasTestTag("mission_detail_sheet")),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntilDoesNotExist(hasTestTag("mission_detail_sheet"), 10_000)
        composeRule.onNodeWithTag("nav_my_patrol").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("my_patrol_list"), 10_000)
        composeRule.onNodeWithText(missionTitle).assertIsDisplayed()
        composeRule.onNodeWithTag("my_patrol_list").performScrollToNode(hasTestTag("start_patrol"))
        composeRule.onNodeWithTag("start_patrol").assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun supervisorMissionCardOpensMissionDetailSheet() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)

        composeRule.onNode(
            hasText("Paused with reason") and hasClickAction(),
        ).performScrollTo().performClick()

        composeRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
        composeRule.onNode(
            hasText("Foot patrol · Market corridor") and
                hasAnyAncestor(hasTestTag("mission_detail_sheet")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun supervisorActiveMissionCardOpensMatchingMissionEvidence() {
        openActiveLocalPatrol()
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)

        composeRule.onNode(
            hasText("Night patrol · Sector 4") and hasClickAction(),
        ).performScrollTo().performClick()

        composeRule.onNodeWithTag("mission_detail_sheet").assertIsDisplayed()
        composeRule.onNode(
            hasText("Night patrol · Sector 4") and
                hasAnyAncestor(hasTestTag("mission_detail_sheet")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun patrolRepositoryBoundsMissionTrackingSession() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.patrolGridRepository.startPatrol()

        assertTrue(app.settingsRepository.get().gpsCaptureEnabled)
        assertTrue(app.settingsRepository.get().activePatrolMissionId != null)

        app.patrolGridRepository.endPatrol()

        assertFalse(app.settingsRepository.get().gpsCaptureEnabled)
        assertNull(app.settingsRepository.get().activePatrolMissionId)
    }

    @Test
    fun expiredDutyWindowStopsCaptureAndClearsTrackingState() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setActivePatrolMission(PatrolGridRepository.PRIMARY_MISSION_ID)
        app.settingsRepository.setGpsEnabled(true)
        app.settingsRepository.setActivePatrolDeadline(System.currentTimeMillis() - 1_000L)

        CaptureController.applyFromSettings(app)

        composeRule.waitUntil(10_000) {
            val settings = app.settingsRepository.get()
            settings.activePatrolMissionId == null &&
                !settings.gpsCaptureEnabled &&
                settings.activePatrolDeadlineMs == null
        }
        val settings = app.settingsRepository.get()
        assertNull(settings.activePatrolMissionId)
        assertFalse(settings.gpsCaptureEnabled)
        assertNull(settings.activePatrolDeadlineMs)
    }

    @Test
    fun supervisorCanReviewMissionStates() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)
        composeRule.onNodeWithText("Needs review").performClick()
        composeRule.onNodeWithText("Review with context").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming").performClick()
        composeRule.onNodeWithText("Day patrol · School corridor").assertIsDisplayed()
    }

    @Test
    fun supervisorReviewRequiresNotesAndSubmitsHumanOutcome() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.patrolGridRepository.startPatrol()
        app.patrolGridRepository.endPatrol()

        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)
        composeRule.onNodeWithText("Needs review").performClick()
        composeRule.onNode(
            hasText("Night patrol · Sector 4") and hasClickAction(),
        ).performClick()

        composeRule.onNodeWithTag("mission_detail_list")
            .performScrollToNode(hasTestTag("review_mission"))
        composeRule.onNodeWithTag("review_mission").performClick()
        composeRule.onNodeWithTag("review_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("submit_review").assertIsNotEnabled()
        composeRule.onNode(
            hasText("Approved") and
                hasClickAction() and
                hasAnyAncestor(hasTestTag("review_dialog")),
        ).assertIsSelected()
        composeRule.onNode(
            hasText("Technically inconclusive") and
                hasClickAction() and
                hasAnyAncestor(hasTestTag("review_dialog")),
        ).performClick().assertIsSelected()
        composeRule.onNode(
            hasText("Cancel") and hasAnyAncestor(hasTestTag("review_dialog")),
        ).performClick()
        composeRule.onNodeWithTag("review_dialog").assertDoesNotExist()

        composeRule.onNodeWithTag("review_mission").performClick()
        composeRule.onNode(
            hasText("Needs context") and
                hasClickAction() and
                hasAnyAncestor(hasTestTag("review_dialog")),
        ).performClick().assertIsSelected()
        composeRule.onNodeWithTag("review_notes")
            .performTextInput("Ask the patrol unit to explain the unvisited canal-road priority.")
        composeRule.onNodeWithTag("submit_review").assertIsEnabled().performClick()
        composeRule.onNodeWithTag("review_dialog").assertDoesNotExist()
    }

    @Test
    fun supervisorAssignsRouteUnitAndFieldGuidance() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)

        composeRule.onNodeWithTag("assign_patrol").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("assignment_sheet"), 10_000)
        composeRule.onNodeWithTag("route_market_loop").performScrollTo().performClick()
        composeRule.onNodeWithTag("unit_9").performScrollTo().performClick()
        composeRule.onNodeWithTag("guidance_area").performScrollTo().performClick()
        composeRule.onNodeWithTag("confirm_assignment").performScrollTo().performClick()

        composeRule.waitUntilAtLeastOneExists(
            androidx.compose.ui.test.hasText("Evening patrol · Market loop"),
            10_000,
        )
        composeRule.onNodeWithText("Evening patrol · Market loop").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Unit 9 · 3 personnel · Flexible area coverage · 3 priority locations",
        ).assertIsDisplayed()
    }

    @Test
    fun supervisorAssignmentAlternativesAreSelectableAndSheetDismisses() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)

        composeRule.onNodeWithTag("assign_patrol").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("assignment_sheet"), 10_000)
        composeRule.onNodeWithTag("route_school_corridor").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("route_night_sector_6").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("unit_7").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("unit_12").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("guidance_suggested").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("guidance_area").performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithTag("guidance_suggested").performScrollTo().performClick().assertIsSelected()

        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntilDoesNotExist(hasTestTag("assignment_sheet"), 10_000)
        composeRule.onNodeWithTag("patrol_control_list").assertIsDisplayed()
    }

    @Test
    fun supervisorUpcomingAssignEntryOpensAssignmentSheet() {
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Supervisor").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("patrol_control_list"), 10_000)

        composeRule.onNodeWithText("Upcoming").performClick()
        composeRule.onNodeWithTag("patrol_control_list")
            .performScrollToNode(hasTestTag("assign_patrol_upcoming"))
        composeRule.onNodeWithTag("assign_patrol_upcoming").performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("assignment_sheet"), 10_000)
        composeRule.onNodeWithTag("assignment_sheet").assertIsDisplayed()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        composeRule.waitUntilDoesNotExist(hasTestTag("assignment_sheet"), 10_000)
    }

    private fun openActiveLocalPatrol() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.patrolGridRepository.startPatrol()
        composeRule.activityRule.scenario.onActivity { activity ->
            ViewModelProvider(activity)
                .get("patrolgrid-local", PatrolGridViewModel::class.java)
                .refreshFromForeground()
        }
        composeRule.waitUntilAtLeastOneExists(hasTestTag("my_patrol_list"), 10_000)
        composeRule.waitUntilAtLeastOneExists(hasText("Tracking active"), 20_000)
    }
}
