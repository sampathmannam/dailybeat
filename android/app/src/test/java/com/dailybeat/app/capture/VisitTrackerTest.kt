package com.dailybeat.app.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.geo.ResolvedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * The capture engine behind every "I stayed at X for 40 minutes" line, exercised on the real
 * IO dispatcher because its recording is dispatched asynchronously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisitTrackerTest {

    private lateinit var db: DailyBeatDb
    private lateinit var scope: CoroutineScope
    private lateinit var recorded: MutableList<LocationVisit>
    private lateinit var tracker: VisitTracker

    /** Rasipuram police station, Tamil Nadu. */
    private val stationLat = 11.4557
    private val stationLon = 78.1856

    private val start = 1_757_000_000_000L // fixed wall clock, ~Sep 2025

    private class StubGeocoder(dao: com.dailybeat.app.data.db.GeocodeDao) : OsmGeocoder(dao) {
        override suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace =
            ResolvedPlace(
                name = "Rasipuram Police Station",
                address = "Rasipuram Police Station, Salem Road, Rasipuram, Tamil Nadu, India",
            )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        recorded = Collections.synchronizedList(mutableListOf())
        tracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = StubGeocoder(db.geocodes()),
            onVisitRecorded = { visit -> recorded.add(visit) },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `a stay keeps the time the officer actually arrived`() {
        // Arrive, linger inside the dwell radius, then walk away far enough to be detected leaving.
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 40.0), stationLon, start + minutes(40))
        tracker.onLocation(offsetLat(stationLat, 400.0), stationLon, start + minutes(45))

        val stay = awaitVisit("dwell")
        assertEquals(
            "The stay was stored at the epoch instead of when the officer arrived, " +
                "so it can never appear under today's date.",
            start,
            stay.startMs,
        )
        assertTrue("The stay must end after it starts.", stay.endMs > stay.startMs)
    }

    @Test
    fun `a long stay survives a departure by vehicle`() {
        // A vehicle leaves faster than the 75 m update filter, so there is no in-radius sample
        // between arriving and being detected elsewhere.
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))

        val stay = awaitVisit("dwell")
        assertEquals(start, stay.startMs)
        assertTrue(
            "A 40 minute stay was discarded because the officer drove away.",
            TimeUnit.MILLISECONDS.toMinutes(stay.endMs - stay.startMs) >= 30,
        )
    }

    @Test
    fun `the journey between two places is recorded`() {
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 30.0), stationLon, start + minutes(30))
        // Leave, stay on the move, then settle somewhere clearly different.
        tracker.onLocation(offsetLat(stationLat, 400.0), stationLon, start + minutes(35))
        tracker.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(41))

        val transit = awaitVisit("transit")
        assertTrue("Transit must start after the epoch.", transit.startMs >= start)
        assertTrue("Transit must end after it starts.", transit.endMs > transit.startMs)
    }

    @Test
    fun `merely passing by is not recorded as a stay`() {
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(3))

        Thread.sleep(1_000)
        assertTrue(
            "A three minute pause was recorded as a stay: ${recorded.map { it.visitType }}",
            recorded.none { it.visitType == "dwell" },
        )
    }

    @Test
    fun `a stay is labelled with the map's own name for the place`() {
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))

        assertEquals("Rasipuram Police Station", awaitVisit("dwell").placeName)
    }

    @Test
    fun `a saved place name wins over the map's name`() = runBlocking {
        PlaceRepository(db.places()).add("My Sub-Division Office", stationLat, stationLon, radiusM = 200)

        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))

        assertEquals("My Sub-Division Office", awaitVisit("dwell").placeName)
    }

    @Test
    fun `an open stay is flushed with its real start time when capture stops`() {
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 40.0), stationLon, start + minutes(40))

        tracker.flushPending()

        val stay = awaitVisit("dwell")
        assertEquals(
            "The flushed stay lost its arrival time.",
            start,
            stay.startMs,
        )
    }

    private fun minutes(count: Long): Long = TimeUnit.MINUTES.toMillis(count)

    /** Moves north by [metres]; longitude is untouched so the maths stays obvious. */
    private fun offsetLat(latitude: Double, metres: Double): Double = latitude + metres / 111_320.0

    private fun awaitVisit(type: String): LocationVisit {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            recorded.firstOrNull { it.visitType == type }?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("No $type visit was recorded. Recorded: $recorded")
    }
}
