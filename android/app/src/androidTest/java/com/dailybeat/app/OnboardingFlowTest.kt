package com.dailybeat.app

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OnboardingFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetAppState() {
        requireDisposableTestApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(
            context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit(),
        )
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun onboardingThreeStepsReachTodayScreen() {
        composeRule.waitUntilAtLeastOneExists(hasText("Continue"), timeoutMillis = 10_000)
        composeRule.onNodeWithText("Continue").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasText("Officer name") and hasSetTextAction(),
            timeoutMillis = 10_000,
        )
        composeRule.onNode(hasText("Officer name") and hasSetTextAction())
            .performTextReplacement("Inspector Rao")

        // Set text through semantics instead of opening Gboard. Waiting for the asynchronous IME
        // hide animation made the following button click depend on emulator frame timing rather
        // than on Daily Beat's onboarding behavior.
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Continue").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Get started"), timeoutMillis = 10_000)
        composeRule.onNodeWithText("Get started").performScrollTo().performClick()

        composeRule.waitUntilAtLeastOneExists(hasTestTag("today_list"), timeoutMillis = 20_000)
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }
}

internal fun grantCorePermissions() {
    requireDisposableTestApp()
    val pkg = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.RECORD_AUDIO,
    ).forEach { permission -> runShellCommand("pm grant $pkg $permission") }
}

/** All test mutations are restricted to a disposable application, even when run directly. */
internal fun requireDisposableTestApp() {
    check(InstrumentationRegistry.getInstrumentation().targetContext.packageName == "com.dailybeat.app.qa.e2eloop") {
        "Tests must use com.dailybeat.app.qa.e2eloop. Production and regular QA data are off limits."
    }
}

/**
 * `executeShellCommand` only queues the command; draining its output is what makes the caller
 * wait for it. Without this, tests race the permission grants they depend on.
 */
private fun runShellCommand(command: String) {
    val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
}
