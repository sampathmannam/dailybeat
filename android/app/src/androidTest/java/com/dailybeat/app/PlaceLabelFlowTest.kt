package com.dailybeat.app

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Local label repair must reach both tabs without changing the captured source records. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlaceLabelFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var app: DailyBeatApp
    private val today get() = DateKeys.today()
    private val dayStart get() = DayBounds.dayStartEnd(today).first

    @Before
    fun prepareDisposableLocalJournal() {
        // These fixtures are destructive only inside the explicitly disposable application.
        // Never operate on the production package or the user's regular QA journal.
        requireDisposableTestApp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        app = ApplicationProvider.getApplicationContext()
        app.settingsRepository.setGpsEnabled(false)
        app.settingsRepository.setGeocodingEndpoint("")
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.clearCapturePause()
        app.mapSettings.setOnline(false)
        runBlocking(Dispatchers.IO) { app.db.clearAllTables() }
        grantCorePermissions()
    }

    @Test
    fun localAddressesAndSavedPlacesRepairBothTabsWithoutRewritingHistory() {
        val transit = visit(0, 11.4557, 78.1856, "Paramathi Road, Namakkal", "transit")
        val addressedStay = visit(1, 11.4600, 78.1900, "Municipal Road, Namakkal")
        val savedStay = visit(2, 11.4700, 78.2000, "Unnamed place")
        val unknownStay = visit(3, 11.4800, 78.2100, "Unnamed place")
        val visits = listOf(transit, addressedStay, savedStay, unknownStay)
        runBlocking(Dispatchers.IO) {
            visits.forEach { captured ->
                app.db.visits().insert(captured)
                app.db.events().insert(generatedMoment(captured))
            }
            app.placeRepository.add("District Library", savedStay.latitude, savedStay.longitude)
        }
        val originalVisits = runBlocking(Dispatchers.IO) { app.db.visits().all() }
        val originalEvents = runBlocking(Dispatchers.IO) { app.db.events().all() }
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("nav_today").performClick()
        assertTextInList("today_list", "Travel · Paramathi Road, Namakkal")
        assertTextInList("today_list", "Stay at Municipal Road, Namakkal")
        assertTextInList("today_list", "Stay at District Library")
        assertTextInList("today_list", "Stay recorded")

        openExpandedDays()
        assertTextInList("feed_list", "Municipal Road")
        assertTextInList("feed_list", "District Library")
        assertTextInList("feed_list", "Tap to name this stop")
        composeRule.onNodeWithText("Unnamed place").assertDoesNotExist()
        composeRule.onNodeWithText("Place name unavailable").performScrollTo().performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("name_place_field"), 10_000)
        assertEquals(
            AnnotatedString(""),
            composeRule.onNodeWithTag("name_place_field")
                .fetchSemanticsNode().config[SemanticsProperties.EditableText],
        )
        composeRule.onNodeWithTag("name_place_field").performTextReplacement("Royal Oak Namakkal")
        composeRule.onNodeWithTag("name_place_save").performClick()

        // The database commit precedes the feed's asynchronous reload. Check both outcomes.
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                app.placeRepository.all().any { it.name == "Royal Oak Namakkal" }
            }
        }
        assertTextInList("feed_list", "Royal Oak Namakkal")
        composeRule.onNodeWithText("Place name unavailable").assertDoesNotExist()

        // Return to the retained Today destination: no Activity recreation or new capture.
        composeRule.onNodeWithTag("nav_today").performClick()
        assertTextInList("today_list", "Stay at Royal Oak Namakkal")
        assertTextInList("today_list", "Travel · Paramathi Road, Namakkal")
        assertEquals(originalVisits, runBlocking(Dispatchers.IO) { app.db.visits().all() })
        assertEquals(originalEvents, runBlocking(Dispatchers.IO) { app.db.events().all() })
        assertEquals("", app.settingsRepository.geocodingEndpoint())
        assertEquals(false, app.mapSettings.state.value.allowOnlineMaps)
    }

    @Test
    fun explicitVisitCorrectionOutranksAnOverlappingSavedPlaceInBothTabs() {
        val corrected = visit(0, 11.4557, 78.1856, "Campus Road, Namakkal").copy(
            placeName = "Clinic Annex",
            manuallyEdited = true,
        )
        val originalMoment = generatedMoment(corrected).copy(
            rawText = "Stay at Broad Campus",
            placeName = "Broad Campus",
        )
        runBlocking(Dispatchers.IO) {
            app.db.visits().insert(corrected)
            app.db.events().insert(originalMoment)
            app.placeRepository.add("Broad Campus", corrected.latitude, corrected.longitude, radiusM = 150)
        }
        val originalVisits = runBlocking(Dispatchers.IO) { app.db.visits().all() }
        val originalEvents = runBlocking(Dispatchers.IO) { app.db.events().all() }
        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithTag("nav_today").performClick()
        assertTextInList("today_list", "Stay at Clinic Annex")
        composeRule.onNodeWithText("Stay at Broad Campus").assertDoesNotExist()
        openExpandedDays()
        assertTextInList("feed_list", "Clinic Annex")
        composeRule.onNodeWithText("Broad Campus").assertDoesNotExist()
        assertEquals(originalVisits, runBlocking(Dispatchers.IO) { app.db.visits().all() })
        assertEquals(originalEvents, runBlocking(Dispatchers.IO) { app.db.events().all() })
    }

    private fun visit(
        index: Int,
        latitude: Double,
        longitude: Double,
        address: String,
        type: String = "dwell",
    ) = LocationVisit(
        startMs = dayStart + (8 * 60L + index * 30L) * 60_000,
        endMs = dayStart + (8 * 60L + index * 30L + 10L) * 60_000,
        latitude = latitude,
        longitude = longitude,
        placeName = "Unnamed place",
        address = address,
        visitType = type,
    )

    private fun generatedMoment(visit: LocationVisit) = Event(
        timestamp = visit.startMs,
        type = "visit",
        rawText = if (visit.visitType == "transit") "Travel recorded" else "Stay at unnamed place",
        placeName = "Unnamed place",
        latitude = visit.latitude,
        longitude = visit.longitude,
    )

    private fun openExpandedDays() {
        composeRule.onNodeWithTag("nav_days").performClick()
        composeRule.waitUntilAtLeastOneExists(hasTestTag("feed_toggle_stops_$today"), 20_000)
        composeRule.onNodeWithTag("feed_toggle_stops_$today").performScrollTo().performClick()
    }

    private fun assertTextInList(listTag: String, text: String) {
        composeRule.waitUntilAtLeastOneExists(hasTestTag(listTag), 20_000)
        // Offscreen lazy rows are not in the semantics tree. Re-scroll while the Room flows
        // settle instead of sleeping or waiting forever for an uncomposed row to appear.
        composeRule.waitUntil(20_000) {
            runCatching {
                composeRule.onNodeWithTag(listTag).performScrollToNode(hasText(text))
                composeRule.onNodeWithText(text).assertIsDisplayed()
            }.isSuccess
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }
}
