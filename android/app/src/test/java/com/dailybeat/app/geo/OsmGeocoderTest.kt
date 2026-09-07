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
        geocoder = OsmGeocoder(db.geocodes(), server.url("/reverse").toString())
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
        server.enqueue(
            MockResponse().setBody(
                """{"display_name":"A, B","namedetails":{"name":"Rasipuram Police Station"}}""",
            ),
        )

        assertEquals("Rasipuram Police Station", geocoder.resolve(lat, lon).name)
        // No second response is queued: a second network call would fail the test.
        assertEquals("Rasipuram Police Station", geocoder.resolve(lat, lon).name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a failed lookup degrades to coordinates instead of throwing`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))

        val resolved = geocoder.resolve(lat, lon)

        assertNull(resolved.name)
        assertTrue("Expected a coordinate fallback, got ${resolved.address}", resolved.address.startsWith("Location "))
    }

    @Test
    fun `malformed map data degrades instead of throwing`() = runBlocking {
        server.enqueue(MockResponse().setBody("not json at all"))

        assertTrue(geocoder.resolve(lat, lon).address.startsWith("Location "))
    }

    @Test
    fun `oversized map response degrades without buffering it`() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(1_100_000)))

        assertTrue(geocoder.resolve(lat, lon).address.startsWith("Location "))
    }

    @Test
    fun `a meaningless coordinate is never sent to the map`() = runBlocking {
        val resolved = geocoder.resolve(0.0, 0.0)

        assertNull(resolved.name)
        assertEquals(0, server.requestCount)
    }
}
