package com.dailybeat.app.ui.feed

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelStore
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.domain.VisitLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * OpenStreetMap has no point of interest at many real stops, so the map can only offer the road
 * it sits on. Naming a stay once has to teach the app for good.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class NamePlaceTest {

    private lateinit var app: DailyBeatApp
    private lateinit var viewModel: FeedViewModel
    private lateinit var viewModelStore: ViewModelStore

    /** A real stop from a captured day where OSM only knew the road. */
    private val stay = DayStay(
        name = "Solakadu - Semmedu - Vilaram - Semmedu Road",
        startMs = 1_757_000_000_000L,
        endMs = 1_757_004_000_000L,
        latitude = 11.286545,
        longitude = 78.337674,
    )

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
        viewModel = FeedViewModel(app)
        viewModelStore = ViewModelStore().also { it.put("feed", viewModel) }
    }

    @After
    fun tearDown() = runBlocking {
        viewModelStore.clear()
        shadowOf(Looper.getMainLooper()).idle()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    /**
     * saveNamedPlace runs on viewModelScope and Room writes on its own executor, so drain the
     * main looper and then wait for the row rather than racing it.
     */
    private fun nameIt(name: String, expectSaved: Boolean = true, selectedStay: DayStay = stay) {
        viewModel.saveNamedPlace(selectedStay, name)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (!viewModel.uiState.value.isSavingPlace) {
                if (expectSaved) assertNull(viewModel.uiState.value.error)
                return
            }
            Thread.sleep(25)
        }
        assertFalse("Naming did not finish", viewModel.uiState.value.isSavingPlace)
    }

    @Test
    fun `naming a stay saves a place at its own coordinates`() = runBlocking {
        nameIt("Rasipuram Police Station")

        val places = withContext(Dispatchers.IO) { app.placeRepository.all() }
        assertEquals(1, places.size)
        assertEquals("Rasipuram Police Station", places.single().name)
        assertEquals(stay.latitude, places.single().latitude, 0.000001)
        assertEquals(stay.longitude, places.single().longitude, 0.000001)
    }

    @Test
    fun `a later stay at the same spot is matched to the saved name`() = runBlocking {
        nameIt("Rasipuram Police Station")
        val places = withContext(Dispatchers.IO) { app.placeRepository.all() }

        // A GPS fix is never identical; the saved radius has to absorb the drift.
        val matched = GeofenceMatcher.matchPlace(
            latitude = stay.latitude + 0.0006,
            longitude = stay.longitude - 0.0004,
            places = places,
        )

        assertEquals("Rasipuram Police Station", matched?.name)
    }

    @Test
    fun `a stay far away is not wrongly matched`() = runBlocking {
        nameIt("Rasipuram Police Station")
        val places = withContext(Dispatchers.IO) { app.placeRepository.all() }

        assertEquals(null, GeofenceMatcher.matchPlace(11.46, 78.19, places))
    }

    @Test
    fun `a blank name is refused rather than saved`() = runBlocking {
        nameIt("   ", expectSaved = false)

        assertTrue(withContext(Dispatchers.IO) { app.placeRepository.all() }.isEmpty())
    }

    @Test
    fun `naming a saved point twice updates one row and preserves its privacy and radius`() = runBlocking {
        nameIt("Camp office")
        val original = withContext(Dispatchers.IO) { app.placeRepository.all().single() }
        withContext(Dispatchers.IO) { app.placeRepository.setPrivate(original, true) }

        nameIt("Royal Oak", selectedStay = stay.copy(name = "Camp office"))

        val renamed = withContext(Dispatchers.IO) { app.placeRepository.all().single() }
        assertEquals(original.id, renamed.id)
        assertEquals(original.radiusM, renamed.radiusM)
        assertTrue(renamed.isPrivate)
        assertEquals("Royal Oak", renamed.name)
        assertEquals("Royal Oak", GeofenceMatcher.matchPlace(stay.latitude, stay.longitude, listOf(renamed))?.name)
    }

    @Test
    fun `naming a stop does not rename a broader overlapping saved place`() = runBlocking {
        withContext(Dispatchers.IO) {
            app.placeRepository.add("Broad campus", stay.latitude + 0.0001, stay.longitude, radiusM = 1000)
        }
        val broader = withContext(Dispatchers.IO) { app.placeRepository.all().single() }

        nameIt("Clinic annex", selectedStay = stay.copy(name = "Broad campus"))

        val places = withContext(Dispatchers.IO) { app.placeRepository.all() }
        assertEquals(2, places.size)
        assertEquals(broader, places.single { it.id == broader.id })
        assertEquals("Clinic annex", GeofenceMatcher.matchPlace(stay.latitude, stay.longitude, places)?.name)
    }

    @Test
    fun `renaming an explicitly corrected stop records an audit and leaves the surrounding place alone`() = runBlocking {
        val original = LocationVisit(
            startMs = stay.startMs, endMs = stay.endMs, latitude = stay.latitude, longitude = stay.longitude,
            placeName = "Clinic annex", manuallyEdited = true,
        )
        val id = withContext(Dispatchers.IO) {
            app.placeRepository.add("Broad campus", stay.latitude, stay.longitude, radiusM = 1000)
            app.db.visits().insert(original)
        }
        val broader = withContext(Dispatchers.IO) { app.placeRepository.all().single() }

        nameIt("Royal Oak", selectedStay = stay.copy(name = "Clinic annex", visitId = id))

        val visit = withContext(Dispatchers.IO) { app.db.visits().all().single() }
        val places = withContext(Dispatchers.IO) { app.placeRepository.all() }
        val correction = withContext(Dispatchers.IO) { app.db.visits().allCorrections().single() }
        assertEquals("Royal Oak", VisitLabels.name(visit, places))
        assertEquals(listOf(broader), places)
        assertEquals(id, correction.visitId)
        assertEquals("placeName", correction.field)
        assertEquals("Clinic annex", correction.oldValue)
        assertEquals("Royal Oak", correction.newValue)
    }

    @Test
    fun `stale saved name cannot create a duplicate at the same point`() = runBlocking {
        nameIt("Camp office")
        nameIt("Royal Oak", expectSaved = false, selectedStay = stay.copy(name = "Old stale label"))

        assertEquals("Camp office", withContext(Dispatchers.IO) { app.placeRepository.all().single().name })
        assertTrue(viewModel.uiState.value.error?.contains("changed") == true)
    }
}
