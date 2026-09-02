package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.ui.patrol.PatrolAssignmentSheet
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PatrolAssignmentSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun unavailableAssignmentExplainsStateAndConnectsRefreshAndCloseControls() {
        var refreshed = false
        var closed = false
        composeRule.setContent {
            DailyBeatTheme {
                PatrolAssignmentSheet(
                    routePlans = emptyList(),
                    unitOptions = emptyList(),
                    onDismiss = { closed = true },
                    onAssign = { error("Assignment must remain unavailable") },
                    onRetry = { refreshed = true },
                )
            }
        }

        composeRule.onNodeWithTag("assignment_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No active routes or staffed patrol units are available",
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("retry_assignment_options").performClick()
        composeRule.onNodeWithTag("close_assignment_options").performClick()

        composeRule.runOnIdle {
            assertTrue(refreshed)
            assertTrue(closed)
        }
    }
}
