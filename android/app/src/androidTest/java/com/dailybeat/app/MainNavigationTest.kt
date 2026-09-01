package com.dailybeat.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.dailybeat.app.data.model.PatrolRole
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
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("patrolgrid_missions", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("Inspector Rao")
        app.settingsRepository.setPatrolRole(PatrolRole.PATROL)
        app.settingsRepository.setActivePatrolMission(null)
        app.settingsRepository.setGpsEnabled(false)
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
        // ActivityScenario recreation preserves the ViewModel. Select the role
        // through the same local preview control a developer uses in the app.
        composeRule.onNodeWithTag("nav_more").performClick()
        composeRule.onNodeWithText("Patrol personnel").performClick()
    }

    @Test
    fun patrolBottomNavigationVisitsAvailableSections() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag("my_patrol_list"), 10_000)
        composeRule.onNodeWithTag("my_patrol_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_missions").performClick()
        composeRule.onNodeWithTag("placeholder_missions").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_messages").performClick()
        composeRule.onNodeWithTag("placeholder_messages").assertIsDisplayed()

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
}
