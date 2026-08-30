package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNodeWithTag("nav_diary").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_history").performClick()
        composeRule.onNodeWithTag("nav_history").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_today").performClick()
        composeRule.onNodeWithTag("today_list").assertIsDisplayed()
    }

    @Test
    fun todayOptionalNoteExpandSaveCollapse() {
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add optional note"))
        composeRule.onNodeWithText("Add optional note").performClick()
        composeRule.onNodeWithTag("today_list")
            .performScrollToNode(hasText("Optional note for today") and hasSetTextAction())
        composeRule.onNode(hasText("Optional note for today") and hasSetTextAction())
            .performTextInput("Patrol briefing at HQ.")
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Save note"))
        composeRule.onNodeWithText("Save note").performClick()
        composeRule.onNodeWithTag("today_list").performScrollToNode(hasText("Add optional note"))
        composeRule.onNodeWithText("Add optional note").assertIsDisplayed()
    }

    @Test
    fun settingsOfficerNameFieldVisible() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Officer name") and hasSetTextAction())
        composeRule.onNode(hasText("Officer name") and hasSetTextAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Cloud AI").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(hasText("Capture"))
        composeRule.onNodeWithText("Capture").assertIsDisplayed()
    }

    @Test
    fun diaryCustomEventsSectionVisible() {
        composeRule.onNodeWithTag("nav_diary").performClick()
        composeRule.onNodeWithText("Custom events (paste)").assertIsDisplayed()
        composeRule.onNodeWithText("Generate from pasted text").assertIsDisplayed()
    }
}
