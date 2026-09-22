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
import kotlinx.coroutines.withTimeout
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

    private class MemoryStateStore : VisitTrackerStateStore {
        var state: VisitTrackerState? = null
        override fun load(): VisitTrackerState? = state
        override fun save(state: VisitTrackerState) { this.state = state }
        override fun clear() { state = null }
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
        observeStay()
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
        // Keep the observed portion of the stay when the next fix is already down the road.
        observeStay(durationMinutes = 38)
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
        observeStay(durationMinutes = 30)
        // Leave, stay on the move, then settle somewhere clearly different.
        tracker.onLocation(offsetLat(stationLat, 400.0), stationLon, start + minutes(35))
        tracker.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(41))
        tracker.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(49))

        val transit = awaitVisit("transit")
        assertTrue("Transit must start after the epoch.", transit.startMs >= start)
        assertTrue("Transit must end after it starts.", transit.endMs > transit.startMs)
        assertEquals(start + minutes(41), transit.endMs)
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
        observeStay(durationMinutes = 38)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))

        assertEquals("Rasipuram Police Station", awaitVisit("dwell").placeName)
    }

    @Test
    fun `a saved place name wins over the map's name`() = runBlocking {
        PlaceRepository(db.places()).add("My Sub-Division Office", stationLat, stationLon, radiusM = 200)

        observeStay(durationMinutes = 38)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))

        assertEquals("My Sub-Division Office", awaitVisit("dwell").placeName)
    }

    @Test
    fun `an open stay is flushed with its real start time when capture stops`() {
        observeStay()

        tracker.flushPending()

        val stay = awaitVisit("dwell")
        assertEquals(
            "The flushed stay lost its arrival time.",
            start,
            stay.startMs,
        )
    }

    @Test
    fun `service teardown saves an open stay without waiting for network geocoding`() {
        var geocoderCalls = 0
        val teardownTracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = object : OsmGeocoder(db.geocodes()) {
                override suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace {
                    geocoderCalls += 1
                    throw AssertionError("Teardown must not make a network lookup")
                }
            },
            onVisitRecorded = { visit -> recorded.add(visit) },
        )
        observeStay(teardownTracker)

        teardownTracker.flushPending(allowNetworkLookup = false)

        val stay = awaitVisit("dwell")
        assertEquals(0, geocoderCalls)
        assertEquals("Unnamed place", stay.address)
    }

    @Test
    fun `privacy erase discards an open stay and its checkpoint`() {
        val store = MemoryStateStore()
        val eraseTracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = StubGeocoder(db.geocodes()),
            onVisitRecorded = { visit -> recorded.add(visit) },
            stateStore = store,
        )
        observeStay(eraseTracker)

        eraseTracker.discardPending()

        Thread.sleep(250)
        assertTrue(recorded.isEmpty())
        assertEquals(null, store.state)
    }

    @Test
    fun `an open stay survives location service recreation`() {
        val store = MemoryStateStore()
        val firstTracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = StubGeocoder(db.geocodes()),
            onVisitRecorded = { visit -> recorded.add(visit) },
            stateStore = store,
        )
        observeStay(firstTracker)

        val restartedTracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = StubGeocoder(db.geocodes()),
            onVisitRecorded = { visit -> recorded.add(visit) },
            stateStore = store,
        )
        restartedTracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(45))

        assertEquals(start, awaitVisit("dwell").startMs)
    }

    @Test
    fun `a lone fix followed by a forty minute gap invents neither a stay nor a drive`() = runBlocking {
        tracker.onLocation(stationLat, stationLon, start)
        tracker.onLocation(offsetLat(stationLat, 900.0), stationLon, start + minutes(40))
        tracker.flushPending()
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `slow walking cannot drag a stop anchor into a false stay`() = runBlocking {
        for (step in 0..20) {
            tracker.onLocation(offsetLat(stationLat, step * 50.0), stationLon, start + minutes(step * 2L))
        }
        tracker.flushPending()
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        assertTrue(recorded.none { it.visitType == "dwell" })
        assertTrue(recorded.any { it.visitType == "transit" })
    }

    @Test
    fun `modest stationary jitter remains one confirmed stay`() = runBlocking {
        for (step in 0..6) {
            val jitterM = if (step == 0) 0.0 else if (step % 2 == 0) 70.0 else -70.0
            tracker.onLocation(offsetLat(stationLat, jitterM), stationLon, start + minutes(step * 2L))
        }
        tracker.flushPending()
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        assertEquals(1, recorded.size)
        assertEquals("dwell", recorded.single().visitType)
        assertEquals(start, recorded.single().startMs)
        assertEquals(start + minutes(12), recorded.single().endMs)
    }

    @Test
    fun `an unobserved gap splits stops without filling the missing half hour`() = runBlocking {
        observeStay(durationMinutes = 10)
        tracker.onLocation(stationLat, stationLon, start + minutes(40))
        tracker.onLocation(stationLat, stationLon, start + minutes(50))
        tracker.flushPending()
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        val stays = recorded.filter { it.visitType == "dwell" }.sortedBy { it.startMs }
        assertEquals(listOf(start, start + minutes(40)), stays.map { it.startMs })
        assertEquals(listOf(start + minutes(10), start + minutes(50)), stays.map { it.endMs })
        assertTrue(recorded.none { it.visitType == "transit" })
    }

    @Test
    fun `confirming an arrival uses its first fix and last observed departure time`() = runBlocking {
        observeStay(durationMinutes = 10)
        tracker.onLocation(offsetLat(stationLat, 400.0), stationLon, start + minutes(11))
        tracker.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(14))
        for (minute in 16L..24L step 2) {
            tracker.onLocation(offsetLat(stationLat, 2000.0), stationLon, start + minutes(minute))
        }
        tracker.onLocation(offsetLat(stationLat, 3000.0), stationLon, start + minutes(28))
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        val journey = recorded.single { it.visitType == "transit" }
        assertEquals(start + minutes(10), journey.startMs)
        assertEquals(start + minutes(16), journey.endMs)
        val arrival = recorded.single { it.visitType == "dwell" && it.startMs > start }
        assertEquals(start + minutes(16), arrival.startMs)
        assertEquals(start + minutes(24), arrival.endMs)
    }

    @Test
    fun `arrival candidate survives process replacement without charging waiting time to travel`() = runBlocking {
        val store = MemoryStateStore()
        fun recreatedTracker() = VisitTracker(scope, PlaceRepository(db.places()), StubGeocoder(db.geocodes()),
            onVisitRecorded = { recorded += it }, stateStore = store)
        val first = recreatedTracker()
        observeStay(first, durationMinutes = 10)
        first.onLocation(offsetLat(stationLat, 1000.0), stationLon, start + minutes(12))
        first.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(14))
        first.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(18))
        withTimeout(10_000L) { first.awaitPendingWrites() }
        assertTrue(store.state!!.isUsable())
        assertTrue(store.state!!.inTransit)
        val restarted = recreatedTracker()
        restarted.onLocation(offsetLat(stationLat, 1500.0), stationLon, start + minutes(22))
        restarted.flushPending()
        withTimeout(10_000L) { restarted.awaitPendingWrites() }
        assertEquals(start + minutes(14), recorded.single { it.visitType == "transit" }.endMs)
        val arrival = recorded.single { it.visitType == "dwell" && it.startMs > start }
        assertEquals(start + minutes(14), arrival.startMs)
        assertEquals(start + minutes(22), arrival.endMs)
    }

    @Test
    fun `poor accuracy or stale fixes cannot move a confirmed stop`() = runBlocking {
        observeStay(durationMinutes = 10)
        tracker.onLocation(offsetLat(stationLat, 2000.0), stationLon, start + minutes(9))
        tracker.onLocation(offsetLat(stationLat, 2000.0), stationLon, start + minutes(12), accuracyM = 200f)
        tracker.onLocation(stationLat, stationLon, start + minutes(14))
        tracker.flushPending()
        withTimeout(10_000L) { tracker.awaitPendingWrites() }
        assertEquals(1, recorded.size)
        assertEquals("dwell", recorded.single().visitType)
        assertEquals(start + minutes(14), recorded.single().endMs)
    }

    @Test
    fun `a trip returning to its origin survives restart while confirming the return`() = runBlocking {
        val store = MemoryStateStore()
        fun recreatedTracker() = VisitTracker(scope, PlaceRepository(db.places()), StubGeocoder(db.geocodes()),
            onVisitRecorded = { recorded += it }, stateStore = store)
        val first = recreatedTracker()
        observeStay(first, durationMinutes = 10)
        first.onLocation(offsetLat(stationLat, 1000.0), stationLon, start + minutes(12))
        first.onLocation(offsetLat(stationLat, 2000.0), stationLon, start + minutes(14))
        first.onLocation(offsetLat(stationLat, 1000.0), stationLon, start + minutes(16))
        first.onLocation(stationLat, stationLon, start + minutes(18))
        first.onLocation(stationLat, stationLon, start + minutes(20))
        withTimeout(10_000L) { first.awaitPendingWrites() }
        assertTrue(store.state!!.maxTransitDisplacementM > 1900.0)

        val restarted = recreatedTracker()
        for (minute in 22L..26L step 2) {
            restarted.onLocation(stationLat, stationLon, start + minutes(minute))
        }
        restarted.flushPending()
        withTimeout(10_000L) { restarted.awaitPendingWrites() }
        val trip = recorded.single { it.visitType == "transit" }
        assertEquals(start + minutes(10), trip.startMs)
        assertEquals(start + minutes(18), trip.endMs)
        val returnedStay = recorded.single { it.visitType == "dwell" && it.startMs > start }
        assertEquals(start + minutes(18), returnedStay.startMs)
        assertEquals(start + minutes(26), returnedStay.endMs)
    }

    private fun observeStay(target: VisitTracker = tracker, durationMinutes: Long = 40) {
        for (minute in 0L..durationMinutes step 2) {
            target.onLocation(stationLat, stationLon, start + minutes(minute))
        }
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
