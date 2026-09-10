package com.dailybeat.app.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.GeocodeDao
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.geo.ResolvedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Filtering a private place out of the cloud payload is not enough on its own: capture used to
 * reverse-geocode every stay the moment it ended, which handed the coordinates of a home or an
 * informant's meeting point to Nominatim before any outbound filter could run.
 *
 * The network call itself has to not happen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateZoneGeocoderGateTest {

    private lateinit var db: DailyBeatDb
    private lateinit var scope: CoroutineScope
    private lateinit var recorded: MutableList<LocationVisit>
    private lateinit var places: PlaceRepository
    private lateinit var geocoder: CountingGeocoder

    private val lat = 11.4557
    private val lon = 78.1856
    private val start = 1_757_000_000_000L

    private companion object {
        const val PRIVATE_RADIUS_M = 150.0
    }

    /** Records what capture asked the network about, so the test can assert on the coordinates. */
    private class CountingGeocoder(dao: GeocodeDao) : OsmGeocoder(dao) {
        val calls = AtomicInteger(0)
        val asked: MutableList<Pair<Double, Double>> = Collections.synchronizedList(mutableListOf())
        override suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace {
            calls.incrementAndGet()
            asked.add(latitude to longitude)
            return ResolvedPlace(
                name = "Rasipuram Police Station",
                address = "Rasipuram Police Station, Salem Road, Rasipuram, Tamil Nadu, India",
            )
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        recorded = Collections.synchronizedList(mutableListOf())
        places = PlaceRepository(db.places())
        geocoder = CountingGeocoder(db.geocodes())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    @Test
    fun `no coordinate inside a private zone is ever sent to the geocoder`() {
        savePlace("Safehouse", isPrivate = true)

        val stay = driveOneDwell()

        // Capture may still geocode a later transit sample once the officer has genuinely left the
        // zone; what must never happen is a lookup on a coordinate inside it.
        val leaked = geocoder.asked.filter { (askedLat, askedLon) ->
            GeofenceMatcher.distanceMeters(askedLat, askedLon, lat, lon) <= PRIVATE_RADIUS_M
        }
        assertTrue("private-zone coordinates were reverse-geocoded: $leaked", leaked.isEmpty())

        // The officer's own name for the place is kept; no third-party address is stored.
        assertEquals("Safehouse", stay.placeName)
        assertNull("a third-party address was stored for a private zone", stay.address)
    }

    @Test
    fun `an ordinary saved place is still geocoded`() {
        savePlace("Station", isPrivate = false)

        val stay = driveOneDwell()

        assertTrue("an ordinary place stopped being geocoded", geocoder.calls.get() > 0)
        assertEquals("Station", stay.placeName)
        assertTrue(stay.address.orEmpty().contains("Rasipuram"))
    }

    @Test
    fun `a stay at no saved place at all is still geocoded`() {
        val stay = driveOneDwell()

        assertTrue(geocoder.calls.get() > 0)
        assertEquals("Rasipuram Police Station", stay.placeName)
    }

    private fun savePlace(name: String, isPrivate: Boolean) = runBlocking {
        places.add(name = name, latitude = lat, longitude = lon, radiusM = PRIVATE_RADIUS_M.toInt())
        val saved = places.all().first { it.name == name }
        if (isPrivate) places.setPrivate(saved, true)
    }

    private fun driveOneDwell(): LocationVisit {
        val tracker = VisitTracker(
            scope = scope,
            placeRepository = places,
            osmGeocoder = geocoder,
            onVisitRecorded = { visit -> recorded.add(visit) },
        )
        // Arrive, linger past the dwell threshold, then move far enough away to close the stay.
        tracker.onLocation(lat, lon, start)
        tracker.onLocation(offsetLat(lat, 40.0), lon, start + minutes(40))
        tracker.onLocation(offsetLat(lat, 400.0), lon, start + minutes(45))
        return awaitVisit("dwell")
    }

    private fun minutes(count: Long): Long = TimeUnit.MINUTES.toMillis(count)

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
