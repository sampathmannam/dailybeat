package com.dailybeat.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.auth.PATROLGRID_PRIVACY_NOTICE_VERSION
import com.dailybeat.app.ui.auth.PatrolGridPrivacyGate
import com.dailybeat.app.ui.auth.PatrolGridPrivacyNoticeScreen
import com.dailybeat.app.ui.auth.isPatrolGridPrivacyPolicyConfigured
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolGridPrivacyNoticeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun materialPolicyUpdateUsesNoticeVersionThree() {
        assertEquals(3, PATROLGRID_PRIVACY_NOTICE_VERSION)
    }

    @Test
    fun operationalPrivacyPolicyRequiresExact365DayRetention() {
        val policyUrl = "https://privacy.example.test/patrolgrid"

        assertTrue(isPatrolGridPrivacyPolicyConfigured(policyUrl, 365))
        assertFalse(isPatrolGridPrivacyPolicyConfigured(policyUrl, 364))
        assertFalse(isPatrolGridPrivacyPolicyConfigured(policyUrl, 366))
    }

    @Test
    fun configuredNoticeBlocksProtectedContentUntilCurrentVersionIsAcknowledged() {
        var acknowledgedVersion by mutableIntStateOf(0)
        composeRule.setContent {
            DailyBeatTheme {
                PatrolGridPrivacyGate(
                    acknowledgedNoticeVersion = acknowledgedVersion,
                    policyUrl = "https://privacy.example.test/patrolgrid",
                    retentionDays = 365,
                    onAcknowledge = { acknowledgedVersion = it },
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.fillMaxSize().testTag("privacy_protected_content"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("privacy_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_protected_content").assertDoesNotExist()
        listOf(
            "privacy_tracking_boundaries",
            "privacy_supervisor_visibility",
            "privacy_human_review",
            "privacy_map_provider",
            "privacy_retention",
            "privacy_context_support",
        ).forEach { tag ->
            composeRule.onNodeWithTag("privacy_notice_list")
                .performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("privacy_notice_list")
            .performScrollToNode(hasTestTag("privacy_retention"))
        composeRule.onNodeWithText("365 days from the mission's first post-patrol review or closure", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_notice_list")
            .performScrollToNode(hasTestTag("privacy_context_support"))
        composeRule.onNodeWithText("no separate technical-support desk", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            "privacy request involving access, correction, export, deletion, or a grievance",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            "subdivision supervisor through the existing official Department channel",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText("normal command, radio, or phone chain", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_notice_list")
            .performScrollToNode(hasTestTag("privacy_acknowledge"))
        composeRule.onNodeWithTag("privacy_acknowledge").performClick()

        composeRule.runOnIdle {
            assertEquals(PATROLGRID_PRIVACY_NOTICE_VERSION, acknowledgedVersion)
        }
        composeRule.onNodeWithTag("privacy_notice").assertDoesNotExist()
        composeRule.onNodeWithTag("privacy_protected_content").assertIsDisplayed()
    }

    @Test
    fun policyLinkUsesConfiguredHttpsDestination() {
        var openedUrl: String? = null
        composeRule.setContent {
            DailyBeatTheme {
                PatrolGridPrivacyNoticeScreen(
                    policyUrl = "https://privacy.example.test/patrolgrid",
                    retentionDays = 365,
                    onAcknowledge = {},
                    onOpenPrivacyPolicy = { openedUrl = it },
                )
            }
        }

        composeRule.onNodeWithTag("privacy_notice_list")
            .performScrollToNode(hasTestTag("privacy_policy_link"))
        composeRule.onNodeWithTag("privacy_policy_link").performClick()

        composeRule.runOnIdle {
            assertEquals("https://privacy.example.test/patrolgrid", openedUrl)
        }
    }

    @Test
    fun unconfiguredNoticeIsScrollableAtLargeTextAndAllowsOnlySyntheticQa() {
        var continued = false
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f),
            ) {
                DailyBeatTheme {
                    PatrolGridPrivacyNoticeScreen(
                        policyUrl = "",
                        retentionDays = 0,
                        onAcknowledge = { continued = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("privacy_policy_unconfigured").assertIsDisplayed()
        composeRule.onNodeWithText("restricted to synthetic QA data", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_policy_link").assertDoesNotExist()
        composeRule.onNodeWithTag("privacy_notice_list")
            .performScrollToNode(hasTestTag("privacy_acknowledge"))
        composeRule.onNodeWithTag("privacy_acknowledge").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertTrue(continued) }
    }
}
