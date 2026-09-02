package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.patrol.PatrolEvidenceIntegrityNotice
import com.dailybeat.app.ui.patrol.PatrolRetentionIncidentAcknowledgement
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolEvidenceIntegrityNoticeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureAndDecryptionFailuresAreExplicitlyVisible() {
        composeRule.setContent {
            DailyBeatTheme {
                PatrolEvidenceIntegrityNotice(
                    unreadableTrackPoints = 2,
                    captureError = "Secure GPS recording stopped",
                )
            }
        }

        composeRule.onNodeWithTag("patrol_evidence_integrity_warning").assertIsDisplayed()
        composeRule.onNodeWithText("Secure GPS recording stopped").assertIsDisplayed()
        composeRule.onNodeWithText("2 encrypted route points are stored", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun retentionCleanupRequiresExplicitReportedAcknowledgement() {
        var acknowledgements = 0
        composeRule.setContent {
            DailyBeatTheme {
                PatrolRetentionIncidentAcknowledgement {
                    acknowledgements += 1
                }
            }
        }

        composeRule.onNodeWithTag("retention_incident_acknowledgement").assertIsDisplayed()
        composeRule.onNodeWithText("subdivision supervisor", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("aggregate count and time", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("acknowledge_retention_incident").performClick()
        composeRule.runOnIdle { assertEquals(1, acknowledgements) }
    }
}
