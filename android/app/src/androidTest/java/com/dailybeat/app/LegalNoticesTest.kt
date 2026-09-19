package com.dailybeat.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegalNoticesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<LegalNoticesActivity>()

    @Test
    fun licencesAreStructuredAndFullTermsOpenOnDemand() {
        composeRule.onNodeWithTag("legal_notices_list").assertIsDisplayed()
        composeRule.onNodeWithText("DailyBeat · GPL-3.0-only").assertIsDisplayed()
        composeRule.onNodeWithTag("gpl_license_content").assertDoesNotExist()

        composeRule.onNodeWithTag("gpl_license_toggle").performClick()

        // The complete GPL text is far taller than a phone viewport. Its existence, rather than
        // full viewport visibility, is the stable assertion that it was created on demand.
        composeRule.onNodeWithTag("gpl_license_content").assertExists()
        composeRule.onNodeWithText("Hide full text").assertExists()
    }
}
