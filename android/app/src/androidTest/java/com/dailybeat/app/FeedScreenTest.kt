package com.dailybeat.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.util.DateKeys
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

        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 10_000)
        composeRule.onNodeWithTag("feed_list")
            .performScrollToNode(hasText("feed_card_${DateKeys.today()}", substring = true))

        // Seeded stays come from the synthetic day and must be named, not coordinates.
        composeRule.onNodeWithText("Police Headquarters").assertIsDisplayed()
        composeRule.onNodeWithText("Stops").assertIsDisplayed()
        composeRule.onNodeWithText("Distance").assertIsDisplayed()
        composeRule.onNodeWithText("Time out").assertIsDisplayed()
    }

    @Test
    fun tappingADayOpensThatDaysDiary() {
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Today"), timeoutMillis = 10_000)

        composeRule.onNodeWithTag("feed_card_${DateKeys.today()}").performClick()

        composeRule.waitUntilAtLeastOneExists(hasText("Custom events (paste)"), timeoutMillis = 10_000)
    }

    @Test
    fun feedTabIsReachableAndEmptyStateIsNotShownForACapturedDay() {
        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Your days"), timeoutMillis = 10_000)

        composeRule.onNodeWithText("No days captured yet").assertDoesNotExist()
    }
}
