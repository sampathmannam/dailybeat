package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun skipOnboarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setOfficerName("IPS Test")
        grantCorePermissions()
        composeRule.activityRule.scenario.recreate()
    }

    @Test
    fun bottomNavVisitsAllFourTabs() {
        composeRule.onNodeWithText("Today").assertIsDisplayed()

        composeRule.onNodeWithText("Diary").performClick()
        composeRule.onNodeWithText("Diary").assertIsDisplayed()

        composeRule.onNodeWithText("History").performClick()
        composeRule.onNodeWithText("History").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()

        composeRule.onNodeWithText("Today").performClick()
        composeRule.onNodeWithText("Passive mode: GPS tracks your journey. Cloud AI writes the official diary at the end of the day.")
            .assertIsDisplayed()
    }

    @Test
    fun todayOptionalNoteExpandSaveCollapse() {
        composeRule.onNodeWithText("Add optional note").performClick()
        composeRule.onNodeWithText("Optional note for today").performTextInput("Patrol briefing at HQ.")
        composeRule.onNodeWithText("Save note").performClick()
        composeRule.onNodeWithText("Add optional note").assertIsDisplayed()
    }

    @Test
    fun settingsOfficerNameFieldVisible() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Officer name").assertIsDisplayed()
        composeRule.onNodeWithText("Cloud AI").assertIsDisplayed()
        composeRule.onNodeWithText("Capture").assertIsDisplayed()
    }

    @Test
    fun diaryCustomEventsSectionVisible() {
        composeRule.onNodeWithText("Diary").performClick()
        composeRule.onNodeWithText("Custom events (paste)").assertIsDisplayed()
        composeRule.onNodeWithText("Generate from pasted text").assertIsDisplayed()
    }
}
