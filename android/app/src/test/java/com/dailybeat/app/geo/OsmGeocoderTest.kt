package com.dailybeat.app.geo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
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
 * The map is where place names come from, so "Rasipuram Police Station" has to survive the
 * round trip rather than degrading into the street or the town.
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
    fun `uses the map's name for a named place`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lat": "11.4557",
                  "lon": "78.1856",
                  "display_name": "Rasipuram Police Station, Salem Road, Rasipuram, Namakkal, Tamil Nadu, 637408, India",
                  "namedetails": { "name": "Rasipuram Police Station" },
                  "address": { "amenity": "Rasipuram Police Station", "road": "Salem Road", "town": "Rasipuram" }
                }
                """.trimIndent(),
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertEquals("Rasipuram Police Station", resolved.name)
        assertEquals("Rasipuram Police Station", resolved.label)
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
                  "display_name": "Salem Road, Rasipuram, Namakkal, Tamil Nadu, India",
                  "address": { "road": "Salem Road", "amenity": "Rasipuram Bus Stand", "town": "Rasipuram" }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("Rasipuram Bus Stand", geocoder.resolve(lat, lon).name)
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
                """{"lat":"11.4557","lon":"78.1856","display_name":"A, B","namedetails":{"name":"Rasipuram Police Station"}}""",
            ),
        )
        assertEquals("Rasipuram Police Station", geocoder.resolve(lat, lon).name)
        // No second response is queued: a second network call would fail the test.
        assertEquals("Rasipuram Police Station", geocoder.resolve(lat, lon).name)
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
                  "display_name": "Royal Oak, Trichy Road, Namakkal, Tamil Nadu, India",
                  "namedetails": { "brand": "Royal Oak" },
                  "address": { "shop": "Royal Oak", "road": "Trichy Road", "town": "Namakkal" }
                }
                """.trimIndent(),
            ),
        )

        val resolved = geocoder.resolve(lat, lon)

        assertEquals("Royal Oak", resolved.name)
        assertTrue(resolved.address.contains("Namakkal"))
        assertTrue(server.takeRequest().requestUrl?.queryParameter("layer") == "poi")
    }

    @Test
    fun `far away poi is rejected and the nearby road is used`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"lat":"12.4557","lon":"79.1856","display_name":"Wrong Shop","name":"Wrong Shop"}""",
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
}
