package com.dailybeat.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.notify.reviewDayIntent
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.Formatters
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReminderNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun reminderColdAndWarmLaunchRetainTheCorrectDayAfterRecreation() {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setGpsEnabled(false)
        app.mapSettings.setOnline(false)
        val yesterday = DateKeys.today().minusDays(1)
        // Start with the reminder action: ActivityScenario tracks filterEquals(intent) and
        // otherwise discards legitimate onResume events after MainActivity.setIntent().
        ActivityScenario.launch<MainActivity>(reviewDayIntent(app, yesterday)).use { scenario ->
            compose.waitUntilAtLeastOneExists(hasTestTag("review_day_screen"), 10_000)
            compose.onNodeWithText(Formatters.dayHeading(yesterday)).assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText(Formatters.dayHeading(yesterday)).assertIsDisplayed()
            compose.onNodeWithTag("review_day_back").performClick()
            compose.onNodeWithTag("today_list").assertIsDisplayed()
            val earlier = yesterday.minusDays(1)
            scenario.onActivity { it.startActivity(reviewDayIntent(it, earlier)) }
            compose.waitUntilAtLeastOneExists(hasText(Formatters.dayHeading(earlier)), 10_000)
            compose.onNodeWithTag("review_day_screen").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithText(Formatters.dayHeading(earlier)).assertIsDisplayed()
        }
    }
}
