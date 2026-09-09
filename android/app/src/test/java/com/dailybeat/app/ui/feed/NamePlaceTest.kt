package com.dailybeat.app.ui.feed

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.domain.GeofenceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    /**
     * saveNamedPlace runs on viewModelScope and Room writes on its own executor, so drain the
     * main looper and then wait for the row rather than racing it.
     */
    private fun nameIt(name: String, expectSaved: Boolean = true) {
        viewModel.saveNamedPlace(stay, name)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            val saved = runBlocking { withContext(Dispatchers.IO) { app.placeRepository.all() } }
            if (saved.isNotEmpty() == expectSaved) return
            Thread.sleep(25)
        }
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
}
