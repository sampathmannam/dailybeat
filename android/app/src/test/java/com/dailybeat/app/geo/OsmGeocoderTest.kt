package com.dailybeat.app.geo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.GeocodeCache
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nearby map objects are useful hints, not proof of entering a venue. Manual saved names are
 * handled separately by VisitTracker and retain their exact labels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OsmGeocoderTest {

    private lateinit var db: DailyBeatDb
    private lateinit var server: MockWebServer
    private lateinit var geocoder: OsmGeocoder

    private val lat = 11.4557
    private val lon = 78.1856

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        geocoder = OsmGeocoder(
            db.geocodes(),
            server.url("/reverse").toString(),
            minimumRequestIntervalMs = 0,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    @Test
    fun `uses a qualified map name for a nearby named place`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lat": "11.4557",
                  "lon": "78.1856",
                  "category": "amenity",
                  "display_name": "Rasipuram Police Station, Salem Road, Rasipuram, Namakkal, Tamil Nadu, 637408, India",
                  "namedetails": { "name": "Rasipuram Police Station" },
                  "address": { "amenity": "Rasipuram Police Station", "road": "Salem Road", "town": "Rasipuram" }
                }
                """.trimIndent(),
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertEquals("Near Rasipuram Police Station", resolved.name)
        assertEquals("Near Rasipuram Police Station", resolved.label)
        assertTrue(resolved.address.contains("Tamil Nadu"))
    }

    @Test
    fun `falls back to a named address feature when there is no name field`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lat": "11.4557",
                  "lon": "78.1856",
                  "category": "amenity",
                  "display_name": "Salem Road, Rasipuram, Namakkal, Tamil Nadu, India",
                  "address": { "road": "Salem Road", "amenity": "Rasipuram Bus Stand", "town": "Rasipuram" }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("Near Rasipuram Bus Stand", geocoder.resolve(lat, lon).name)
    }

    @Test
    fun `an unnamed spot still gets a usable label`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"No nearby POI"}"""))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "display_name": "Salem Road, Rasipuram, Tamil Nadu, India",
                  "address": { "road": "Salem Road" }
                }
                """.trimIndent(),
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertNull(resolved.name)
        assertEquals("Salem Road", resolved.label)
    }

    @Test
    fun `the name is cached so the same spot is not looked up twice`() = runBlocking {
        // Official Nominatim results include the result centroid, which lets DailyBeat reject a
        // venue that is too far from the captured stop.
        server.enqueue(
            MockResponse().setBody(
                """{"lat":"11.4557","lon":"78.1856","category":"amenity","display_name":"A, B","namedetails":{"name":"Rasipuram Police Station"}}""",
            ),
        )
        assertEquals("Near Rasipuram Police Station", geocoder.resolve(lat, lon).name)
        // No second response is queued: a second network call would fail the test.
        assertEquals("Near Rasipuram Police Station", geocoder.resolve(lat, lon).name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed lookup keeps coordinates out of text labels`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val resolved = geocoder.resolve(lat, lon)

        assertNull(resolved.name)
        assertEquals("Unnamed place", resolved.address)
    }

    @Test
    fun `malformed map data degrades instead of throwing`() = runBlocking {
        server.enqueue(MockResponse().setBody("not json at all"))

        assertEquals("Unnamed place", geocoder.resolve(lat, lon).address)
    }

    @Test
    fun `oversized map response degrades without buffering it`() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(1_100_000)))

        assertEquals("Unnamed place", geocoder.resolve(lat, lon).address)
    }

    @Test
    fun `a meaningless coordinate is never sent to the map`() = runBlocking {
        val resolved = geocoder.resolve(0.0, 0.0)

        assertNull(resolved.name)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `nearby named shop wins over the road address`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lat": "11.4558",
                  "lon": "78.1857",
                  "category": "shop",
                  "display_name": "Royal Oak, Trichy Road, Namakkal, Tamil Nadu, India",
                  "namedetails": { "brand": "Royal Oak" },
                  "address": { "shop": "Royal Oak", "road": "Trichy Road", "town": "Namakkal" }
                }
                """.trimIndent(),
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertEquals("Near Royal Oak", resolved.name)
        assertTrue(resolved.address.contains("Namakkal"))
        assertTrue(server.takeRequest().requestUrl?.queryParameter("layer") == "poi")
    }

    @Test
    fun `far away poi is rejected and the nearby road is used`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"lat":"12.4557","lon":"79.1856","category":"shop","display_name":"Wrong Shop","name":"Wrong Shop"}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"lat":"11.4557","lon":"78.1856","display_name":"Trichy Road, Namakkal","address":{"road":"Trichy Road"}}""",
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertNull(resolved.name)
        assertEquals("Trichy Road", resolved.label)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a shop 150 metres down the road is not claimed as the stop`() = runBlocking {
        enqueuePoi("""{"lat":"11.45705","lon":"78.1856","category":"shop","name":"Wrong Shop"}""")
        enqueueRoad()

        val resolved = geocoder.resolve(lat, lon)

        assertNull(resolved.name)
        assertEquals("Trichy Road", resolved.label)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `road and town results cannot masquerade as venues when endpoint ignores layer`() = runBlocking {
        for (category in listOf("highway", "boundary", "place", "")) {
            enqueuePoi("""{"lat":"11.4557","lon":"78.1856","category":"$category","name":"Not a venue"}""")
            enqueueRoad()
            db.geocodes().deleteAll()
            assertNull(geocoder.resolve(lat, lon).name)
        }
    }

    @Test
    fun `venue own name takes priority over brand and operator`() = runBlocking {
        enqueuePoi("""{"lat":"11.4557","lon":"78.1856","category":"shop","name":"Royal Oak Namakkal","namedetails":{"brand":"Furniture Chain","operator":"Holding Company"}}""")

        assertEquals("Near Royal Oak Namakkal", geocoder.resolve(lat, lon).name)
    }

    @Test
    fun `an operator alone is not a venue name`() = runBlocking {
        enqueuePoi("""{"lat":"11.4557","lon":"78.1856","category":"amenity","extratags":{"operator":"Town Council"}}""")
        enqueueRoad()

        assertNull(geocoder.resolve(lat, lon).name)
    }

    @Test
    fun `out of range returned coordinates are not accepted after spherical wraparound`() = runBlocking {
        enqueuePoi("""{"lat":"11.4557","lon":"438.1856","category":"shop","name":"Wrong Shop"}""")
        enqueueRoad()

        assertNull(geocoder.resolve(lat, lon).name)
    }

    @Test
    fun `old exact name guesses are not reused from cache`() = runBlocking {
        db.geocodes().put(GeocodeCache(
            key = server.url("/reverse").toString() + ":v2:11.4557,78.1856",
            displayName = "Wrong Shop, Old address", placeName = "Wrong Shop",
        ))
        enqueuePoi("""{"lat":"11.4557","lon":"78.1856","category":"shop","name":"Current Shop"}""")

        assertEquals("Near Current Shop", geocoder.resolve(lat, lon).name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired map names are refreshed without changing recorded history`() = runBlocking {
        db.geocodes().put(GeocodeCache(
            key = server.url("/reverse").toString() + ":v3:11.4557,78.1856",
            displayName = "Near Former Shop", placeName = "Near Former Shop", fetchedAt = 1L,
        ))
        enqueuePoi("""{"lat":"11.4557","lon":"78.1856","category":"shop","name":"Current Shop"}""")

        assertEquals("Near Current Shop", geocoder.resolve(lat, lon).name)
    }

    private fun enqueuePoi(json: String) {
        server.enqueue(MockResponse().setBody(json))
    }

    private fun enqueueRoad() {
        server.enqueue(MockResponse().setBody(
            """{"lat":"11.4557","lon":"78.1856","category":"highway","name":"Trichy Road","display_name":"Trichy Road, Namakkal","address":{"road":"Trichy Road"}}""",
        ))
    }
}
