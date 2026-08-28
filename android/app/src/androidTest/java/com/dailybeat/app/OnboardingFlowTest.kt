package com.dailybeat.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun onboardingThreeStepsReachTodayScreen() {
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Officer name").performTextInput("Inspector Rao")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Passive mode: GPS tracks your journey. Cloud AI writes the official diary at the end of the day.")
            .assertIsDisplayed()
    }
}

internal fun grantCorePermissions() {
    val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val shell = InstrumentationRegistry.getInstrumentation().uiAutomation
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_FINE_LOCATION}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_COARSE_LOCATION}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.ACCESS_BACKGROUND_LOCATION}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.POST_NOTIFICATIONS}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.READ_CALL_LOG}")
    shell.executeShellCommand("pm grant $pkg ${Manifest.permission.RECORD_AUDIO}")
}
