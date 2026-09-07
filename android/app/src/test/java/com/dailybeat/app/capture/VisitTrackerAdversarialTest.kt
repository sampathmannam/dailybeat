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
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * GPS hardware lies: it repeats fixes, jumps continents, reports the epoch, and goes backwards
 * in time. None of that may crash capture or produce a visit the rest of the app cannot show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VisitTrackerAdversarialTest {

    private lateinit var db: DailyBeatDb
    private lateinit var scope: CoroutineScope
    private lateinit var recorded: MutableList<LocationVisit>
    private lateinit var tracker: VisitTracker

    private val start = 1_757_000_000_000L

    private class StubGeocoder(dao: com.dailybeat.app.data.db.GeocodeDao) : OsmGeocoder(dao) {
        override suspend fun resolve(latitude: Double, longitude: Double) =
            ResolvedPlace(name = "Somewhere", address = "Somewhere, Tamil Nadu, India")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        recorded = Collections.synchronizedList(mutableListOf())
        tracker = VisitTracker(
            scope = scope,
            placeRepository = PlaceRepository(db.places()),
            osmGeocoder = StubGeocoder(db.geocodes()),
            onVisitRecorded = { recorded.add(it) },
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `random noise never produces an unusable visit`() {
        val random = Random(20260907)
        repeat(4_000) { i ->
            tracker.onLocation(
                latitude = random.nextDouble(-90.0, 90.0),
                longitude = random.nextDouble(-180.0, 180.0),
                timestampMs = start + random.nextLong(-TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(1)) + i,
            )
        }
        tracker.flushPending()
        drain()

        assertAllVisitsUsable()
    }

    @Test
    fun `timestamps that go backwards never invert a visit`() {
        var t = start + TimeUnit.HOURS.toMillis(12)
        repeat(300) {
            tracker.onLocation(11.4557 + it * 0.004, 78.1856, t)
            t -= TimeUnit.MINUTES.toMillis(3) // clock running backwards
        }
        tracker.flushPending()
        drain()

        assertAllVisitsUsable()
    }

    @Test
    fun `a stuck GPS repeating one fix does not spam visits`() {
        repeat(500) { i ->
            tracker.onLocation(11.4557, 78.1856, start + TimeUnit.MINUTES.toMillis(i.toLong()))
        }
        drain()

        assertTrue(
            "A stationary phone produced ${recorded.size} visits; it should stay quiet until it moves.",
            recorded.size <= 1,
        )
    }

    @Test
    fun `teleporting between hemispheres does not crash or invert a visit`() {
        val hops = listOf(
            11.4557 to 78.1856, -33.86 to 151.20, 64.13 to -21.89,
            -54.8 to -68.3, 78.2 to 15.6, 0.0 to 179.999, 0.0 to -179.999,
        )
        hops.forEachIndexed { i, (lat, lon) ->
            tracker.onLocation(lat, lon, start + TimeUnit.MINUTES.toMillis(i * 20L))
        }
        tracker.flushPending()
        drain()

        assertAllVisitsUsable()
    }

    @Test
    fun `extreme and degenerate coordinates are tolerated`() {
        listOf(
            0.0 to 0.0,
            90.0 to 180.0,
            -90.0 to -180.0,
            1e-12 to 1e-12,
        ).forEachIndexed { i, (lat, lon) ->
            tracker.onLocation(lat, lon, start + TimeUnit.MINUTES.toMillis(i * 30L))
        }
        tracker.flushPending()
        drain()

        assertAllVisitsUsable()
    }

    @Test
    fun `flushing repeatedly does not duplicate or resurrect a visit`() {
        tracker.onLocation(11.4557, 78.1856, start)
        tracker.onLocation(11.4560, 78.1856, start + TimeUnit.MINUTES.toMillis(40))
        tracker.flushPending()
        drain()
        val afterFirst = recorded.size

        repeat(5) { tracker.flushPending() }
        drain()

        assertTrue(
            "Repeated flushes added visits: $afterFirst -> ${recorded.size}",
            recorded.size == afterFirst,
        )
    }

    /** Lets the IO-dispatched recording settle before asserting. */
    private fun drain() = Thread.sleep(1_500)

    private fun assertAllVisitsUsable() {
        recorded.toList().forEach { v ->
            assertTrue("Visit stored at the epoch cannot match any day: $v", v.startMs > 0)
            assertTrue("Visit ends before it starts: $v", v.endMs >= v.startMs)
            assertTrue("Latitude off the planet: $v", v.latitude in -90.0..90.0)
            assertTrue("Longitude off the planet: $v", v.longitude in -180.0..180.0)
            assertTrue("Visit has no type: $v", v.visitType.isNotBlank())
        }
    }
}
