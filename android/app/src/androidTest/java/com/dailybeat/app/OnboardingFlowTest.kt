package com.dailybeat.app

import android.Manifest
import android.os.ParcelFileDescriptor
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
        composeRule.onNode(hasText("Officer name") and hasSetTextAction())
            .performTextInput("Inspector Rao")
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Get started").performClick()

        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }
}

internal fun grantCorePermissions() {
    val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.RECORD_AUDIO,
    ).forEach { permission -> runShellCommand("pm grant $pkg $permission") }
}

/**
 * `executeShellCommand` only queues the command; draining its output is what makes the caller
 * wait for it. Without this, tests race the permission grants they depend on.
 */
private fun runShellCommand(command: String) {
    val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
}
