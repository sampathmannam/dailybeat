package com.dailybeat.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WholeDayBeatTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: DailyBeatApp

    @Before
    fun seedBeat() {
        requireDisposableTestApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        app = ApplicationProvider.getApplicationContext()
        runBlocking(Dispatchers.IO) {
            app.db.clearAllTables()
            SyntheticDayGenerator.seedToday(app)
        }
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setGpsEnabled(false)
        app.settingsRepository.clearCapturePause()
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun aCapturedDayCanBeNamedCompletedAndSeenInDays() {
        openTodayReview()
        composeRule.onNodeWithTag("beat_title").performTextReplacement("District rounds and court duty")
        composeRule.onNodeWithTag("review_day_screen").performScrollToNode(hasTestTag("complete_beat"))
        composeRule.onNodeWithTag("complete_beat").performClick()

        composeRule.waitUntil(10_000) {
            runBlocking { app.beatRepository.get(DateKeys.today())?.state == "complete" }
        }
        composeRule.onNodeWithText("Reopen for corrections").assertIsDisplayed()

        composeRule.onNodeWithTag("review_day_screen").performScrollToNode(hasTestTag("review_day_back"))
        composeRule.onNodeWithTag("review_day_back").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("feed_list"), timeoutMillis = 10_000)
        // Returning to the retained Days destination refreshes asynchronously. Wait for the new
        // title to reach its semantics tree before asking the lazy list to scroll to it.
        composeRule.waitUntilAtLeastOneExists(
            hasText("District rounds and court duty"),
            timeoutMillis = 10_000,
        )
        composeRule.onNodeWithTag("feed_list")
            .performScrollToNode(hasText("District rounds and court duty"))
        composeRule.onNodeWithText("District rounds and court duty").assertIsDisplayed()
        composeRule.onNodeWithText("Complete").assertIsDisplayed()
    }

    @Test
    fun aWrongStopCanBeHiddenWithoutDeletingIt() {
        val firstVisit = runBlocking(Dispatchers.IO) { app.visitRepository.visitsForDate(DateKeys.today()).first() }
        openTodayReview()
        composeRule.onNodeWithTag("review_day_screen")
            .performScrollToNode(hasTestTag("review_visit_${firstVisit.id}"))
        composeRule.onNodeWithTag("hide_visit_${firstVisit.id}").performClick()

        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                app.visitRepository.visitsForDate(DateKeys.today()).first { it.id == firstVisit.id }.hidden
            }
        }
        val persisted = runBlocking(Dispatchers.IO) {
            app.visitRepository.visitsForDate(DateKeys.today()).first { it.id == firstVisit.id }
        }
        assertTrue(persisted.manuallyEdited)
        assertEquals(firstVisit.placeName, persisted.placeName)
        composeRule.onNodeWithText("Restore").assertIsDisplayed()
    }

    @Test
    fun insightsTurnHistoryIntoAConcreteNextAction() {
        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("actionable_insight"), timeoutMillis = 20_000)
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("actionable_insight"))
        composeRule.onNodeWithText("Close today’s loop").assertIsDisplayed()
        composeRule.onNodeWithText("Review my day").assertIsDisplayed()
    }

    private fun openTodayReview() {
        composeRule.onNodeWithTag("nav_days").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasTestTag("feed_card_${DateKeys.today()}"),
            timeoutMillis = 20_000,
        )
        composeRule.onNodeWithTag("feed_card_${DateKeys.today()}").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("review_day_screen"), timeoutMillis = 10_000)
    }
}
