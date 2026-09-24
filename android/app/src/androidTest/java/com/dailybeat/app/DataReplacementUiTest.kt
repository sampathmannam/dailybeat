package com.dailybeat.app

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Retained screens must dismiss private drafts when their underlying journal is replaced. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DataReplacementUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var app: DailyBeatApp
    private var visitId = 0L

    @Before fun prepare() {
        requireDisposableTestApp()
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        app.settingsRepository.setGpsEnabled(false)
        app.settingsRepository.setGeocodingEndpoint("")
        app.settingsRepository.setOnboardingComplete(true)
        app.mapSettings.setOnline(false)
        runBlocking(Dispatchers.IO) {
            app.db.clearAllTables()
            val start = DayBounds.dayStartEnd(DateKeys.today()).first + 60_000L
            visitId = app.visitRepository.insert(LocationVisit(startMs = start, endMs = start + 600_000L,
                latitude = 11.4, longitude = 78.2, placeName = "Private fixture stop", visitType = "dwell"))
            app.diaryRepository.saveForDate(DateKeys.today(), "Private fixture diary")
        }
        grantCorePermissions()
        compose.activityRule.scenario.recreate()
    }

    @Test fun eraseDismissesDaysNamingDialogAndRemovesOldCards() {
        openDays()
        compose.onNodeWithTag("feed_toggle_stops_${DateKeys.today()}").performScrollTo().performClick()
        compose.onNodeWithText("Private fixture stop").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("name_place_field"), 10_000)
        compose.onNodeWithTag("name_place_field").performTextReplacement("Unsaved private name")

        eraseJournal()

        waitForDialogToDisappear("name_place_field")
        compose.onNodeWithTag("feed_card_${DateKeys.today()}").assertDoesNotExist()
        compose.onNodeWithText("Unsaved private name").assertDoesNotExist()
        assertEmptyJournal()
    }

    @Test fun eraseDismissesPreparedSharingCopy() {
        openDays()
        compose.onNodeWithTag("feed_list").performScrollToNode(hasText(app.getString(R.string.export_week_package)))
        compose.onNodeWithText(app.getString(R.string.export_week_package)).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("share_preview"), 10_000)

        eraseJournal()

        waitForDialogToDisappear("share_preview")
        compose.onNodeWithText("Private fixture diary").assertDoesNotExist()
        assertEmptyJournal()
    }

    @Test fun eraseDismissesReviewCorrectionDialog() {
        compose.onNodeWithTag("today_list").performScrollToNode(hasText("Review my day"))
        compose.onNodeWithText("Review my day").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("review_day_screen"), 10_000)
        compose.onNodeWithTag("review_day_screen").performScrollToNode(hasTestTag("review_visit_$visitId"))
        compose.onNodeWithTag("rename_visit_$visitId").performClick()
        compose.onNodeWithTag("rename_stop_name").performTextReplacement("Stale correction")

        eraseJournal()

        waitForDialogToDisappear("rename_stop_name")
        compose.onNodeWithText("Stale correction").assertDoesNotExist()
        assertEmptyJournal()
    }

    private fun openDays() {
        compose.onNodeWithTag("nav_days").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("feed_card_${DateKeys.today()}"), 15_000)
    }

    private fun eraseJournal() = runBlocking(Dispatchers.IO) {
        CaptureStorageGate.mutex.withLock {
            app.db.clearAllTables()
            CaptureStorageGate.invalidatePersonalData()
        }
    }

    private fun waitForDialogToDisappear(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    private fun assertEmptyJournal() = runBlocking(Dispatchers.IO) {
        assertTrue(app.db.visits().all().isEmpty())
        assertTrue(app.placeRepository.all().isEmpty())
        assertTrue(app.beatRepository.all().isEmpty())
    }
}
