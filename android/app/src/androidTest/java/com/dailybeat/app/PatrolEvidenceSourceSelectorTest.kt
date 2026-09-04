package com.dailybeat.app

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.patrolgrid.PatrolEvidenceSource
import com.dailybeat.app.ui.patrol.PatrolEvidenceSourceSelector
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolEvidenceSourceSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sourcesRemainSeparateAndExposeHumanReadableProvenance() {
        var selectedSession: String? = null
        val first = source(
            sessionId = "session-secret-one",
            displayName = "Officer One",
            badgeNumber = "B-17",
            pointCount = 42,
        )
        val second = source(
            sessionId = "session-secret-two",
            displayName = "Officer Two",
            badgeNumber = null,
            pointCount = 19,
        )
        composeRule.setContent {
            DailyBeatTheme {
                PatrolEvidenceSourceSelector(
                    sources = listOf(first, second),
                    selectedSessionId = first.sessionId,
                    loading = false,
                    error = null,
                    onSelect = { selectedSession = it },
                )
            }
        }

        composeRule.onNodeWithTag("evidence_source_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("evidence_source_${first.sessionId}").assertIsSelected()
        composeRule.onNodeWithText("Officer One · B-17").assertIsDisplayed()
        composeRule.onNodeWithText("42 server-received GPS points", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("PatrolGrid app 1.4.0").assertIsDisplayed()
        composeRule.onNodeWithText("not proof by itself", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(first.sessionId).assertDoesNotExist()

        composeRule.onNodeWithTag("evidence_source_${second.sessionId}")
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(second.sessionId, selectedSession) }
    }

    private fun source(
        sessionId: String,
        displayName: String,
        badgeNumber: String?,
        pointCount: Int,
    ) = PatrolEvidenceSource(
        sessionId = sessionId,
        userId = "user-$sessionId",
        displayName = displayName,
        badgeNumber = badgeNumber,
        startedAtMs = 1_788_280_200_000L,
        endedAtMs = 1_788_294_600_000L,
        endReason = "completed",
        appVersion = "1.4.0",
        trackPointCount = pointCount,
        firstRecordedAtMs = 1_788_280_210_000L,
        lastRecordedAtMs = 1_788_294_590_000L,
        firstReceivedAtMs = 1_788_280_211_000L,
        lastReceivedAtMs = 1_788_294_591_000L,
        bestAccuracyM = 3.5f,
        worstAccuracyM = 18.25f,
    )
}
