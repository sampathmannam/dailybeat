package com.dailybeat.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import com.dailybeat.app.data.model.LocationVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The feed is the visual answer to "where did I go?", so it has to show a real day's stops,
 * their durations, and route the officer through to that day's diary.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class FeedScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seedADay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.dailybeat.app.qa.e2eloop") {
            "Destructive feed tests require the disposable .qa.e2eloop package."
        }
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        runBlocking {
            withContext(Dispatchers.IO) {
                app.db.clearAllTables()
                com.dailybeat.app.synthetic.SyntheticDayGenerator.seedToday(app)
            }
        }
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("IPS Test")
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun feedShowsTodayAsACardWithStopsAndDurations() {
        composeRule.onNodeWithTag("nav_history").performClick()

        composeRule.waitUntilAtLeastOneExists(
            hasTestTag("feed_card_${DateKeys.today()}"),
            timeoutMillis = 20_000,
        )
        composeRule.onNodeWithTag("feed_list")
            .performScrollToNode(hasTestTag("feed_card_${DateKeys.today()}"))

        // Seeded stays come from the synthetic day and must be named, not coordinates.
        composeRule.onNodeWithText("Police Headquarters").assertIsDisplayed()
        composeRule.onNodeWithText("Stops").assertIsDisplayed()
        composeRule.onNodeWithText("Distance").assertIsDisplayed()
        composeRule.onNodeWithText("Time out").assertIsDisplayed()
    }

    @Test
    fun tappingADayOpensThatDaysDiary() {
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasTestTag("feed_card_${DateKeys.today()}"),
            timeoutMillis = 20_000,
        )

        composeRule.onNodeWithTag("feed_card_${DateKeys.today()}").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Custom events (paste)"), timeoutMillis = 10_000)
    }

    @Test
    fun feedTabIsReachableAndEmptyStateIsNotShownForACapturedDay() {
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Your days"), timeoutMillis = 10_000)

        composeRule.onNodeWithText("No days captured yet").assertDoesNotExist()
    }

    @Test
    fun allStopsCanBeExpandedInsteadOfRemainingHidden() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val dayStart = DayBounds.dayStartEnd(DateKeys.today()).first
        runBlocking {
            withContext(Dispatchers.IO) {
                app.db.clearAllTables()
                repeat(8) { index ->
                    app.visitRepository.insert(
                        LocationVisit(
                            startMs = dayStart + index * 20 * 60_000L,
                            endMs = dayStart + (index * 20 + 10) * 60_000L,
                            latitude = 11.4557 + index * 0.001,
                            longitude = 78.1856,
                            placeName = "Stop ${index + 1}",
                            visitType = "dwell",
                        ),
                    )
                }
            }
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("+3 more stops"), timeoutMillis = 20_000)

        // A lazy card may exist below the viewport. Scroll the actual control into view
        // before tapping; otherwise the gesture can hit the bottom navigation instead.
        composeRule.onNodeWithTag("feed_toggle_stops_${DateKeys.today()}")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("feed_list").performScrollToNode(hasText("Stop 8"))

        composeRule.onNodeWithText("Stop 8").assertIsDisplayed()
        composeRule.activityRule.scenario.recreate()
        waitForFeedRefresh()
        composeRule.onNodeWithTag("feed_list").performScrollToNode(hasText("Stop 8"))
        composeRule.onNodeWithText("Stop 8").assertIsDisplayed()
    }

    @Test
    fun returningToFeedLoadsDaysCapturedWhileItWasAway() {
        val yesterday = DateKeys.today().minusDays(1)
        val dayStart = DayBounds.dayStartEnd(yesterday).first
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(
            hasTestTag("feed_card_${DateKeys.today()}"),
            timeoutMillis = 20_000,
        )
        composeRule.onNodeWithTag("feed_card_$yesterday").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_today").performClick()
        runBlocking {
            withContext(Dispatchers.IO) {
                app.visitRepository.insert(
                    LocationVisit(
                        startMs = dayStart + 60_000L,
                        endMs = dayStart + 20 * 60_000L,
                        latitude = 11.4557,
                        longitude = 78.1856,
                        placeName = "Yesterday Station",
                        visitType = "dwell",
                    ),
                )
            }
        }

        composeRule.onNodeWithTag("nav_history").performClick()
        waitForFeedRefresh()
        // Unseen lazy-list rows are not composed. Waiting for their semantics without
        // scrolling incorrectly reports a missing day on a smaller viewport.
        composeRule.onNodeWithTag("feed_list").performScrollToNode(hasTestTag("feed_card_$yesterday"))

        composeRule.onNodeWithTag("feed_card_$yesterday").assertIsDisplayed()
    }

    @Test
    fun foregroundingFeedLoadsVisitsCapturedInTheBackground() {
        val yesterday = DateKeys.today().minusDays(1)
        val dayStart = DayBounds.dayStartEnd(yesterday).first
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()

        composeRule.onNodeWithTag("nav_history").performClick()
        waitForFeedRefresh()
        // Stop and resume the same Activity, without recreating it or switching tabs.
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        runBlocking {
            withContext(Dispatchers.IO) {
                app.visitRepository.insert(
                    LocationVisit(
                        startMs = dayStart + 60_000L,
                        endMs = dayStart + 20 * 60_000L,
                        latitude = 11.4557,
                        longitude = 78.1856,
                        placeName = "Background Station",
                        visitType = "dwell",
                    ),
                )
            }
        }
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForFeedRefresh()
        composeRule.onNodeWithTag("feed_list").performScrollToNode(hasTestTag("feed_card_$yesterday"))
        composeRule.onNodeWithTag("feed_card_$yesterday").assertIsDisplayed()
        composeRule.onNodeWithText("Background Station").assertIsDisplayed()
    }

    private fun waitForFeedRefresh() {
        composeRule.waitUntilAtLeastOneExists(hasTestTag("feed_list"), timeoutMillis = 20_000)
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithTag("feed_loading").fetchSemanticsNodes().isEmpty()
        }
    }
}
