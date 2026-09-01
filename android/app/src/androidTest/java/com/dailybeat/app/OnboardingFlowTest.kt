package com.dailybeat.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun onboardingThreeStepsReachMyPatrolScreen() {
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNode(hasText("Officer name") and hasSetTextAction())
            .performTextInput("Inspector Rao")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.onNodeWithTag("my_patrol_list").assertIsDisplayed()
    }
}

internal fun grantCorePermissions() {
    val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_FINE_LOCATION}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_COARSE_LOCATION}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}")
}
