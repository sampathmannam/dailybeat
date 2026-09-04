package com.dailybeat.app

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.auth.PatrolGridLoginScreen
import com.dailybeat.app.ui.theme.DailyBeatTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolGridLoginScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun managedLoginValidatesFieldsTogglesPasswordAndSubmitsTrimmedIdentity() {
        val submitted = AtomicReference<Pair<String, String>?>()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                DailyBeatTheme {
                    PatrolGridLoginScreen(
                        loading = false,
                        error = null,
                        onSignIn = { email, password -> submitted.set(email to password) },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_email").performTextInput("  inspector@example.gov.in  ")
        composeRule.onNodeWithTag("login_password").performTextInput("correct horse battery staple")
        composeRule.onNodeWithTag("login_password_visibility").performClick()
        composeRule.onNodeWithTag("login_password_visibility").performClick()
        composeRule.onNodeWithTag("login_submit").assertIsEnabled().performClick()

        composeRule.runOnIdle {
            assertEquals(
                "inspector@example.gov.in" to "correct horse battery staple",
                submitted.get(),
            )
        }
    }

    @Test
    fun managedLoginShowsFailureAndDisablesControlsWhileSubmitting() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                DailyBeatTheme {
                    PatrolGridLoginScreen(
                        loading = true,
                        error = "PatrolGrid could not connect securely.",
                        onSignIn = { _, _ -> error("Disabled sign-in must not run") },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("login_email").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_password").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("login_error").assertIsDisplayed()
        composeRule.onNodeWithText("Signing in…").assertIsDisplayed()
    }

    @Test
    fun lockedLoginRequiresReauthenticationWithoutClaimingTrackingStopped() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    DailyBeatTheme {
                        PatrolGridLoginScreen(
                            loading = false,
                            error = null,
                            locked = true,
                            onSignIn = { _, _ -> Unit },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Unlock PatrolGrid").assertIsDisplayed()
        composeRule.onNodeWithText(
            "secure synchronization and active patrol tracking are not interrupted",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("login_submit").performScrollTo().assertIsNotEnabled()
    }
}
