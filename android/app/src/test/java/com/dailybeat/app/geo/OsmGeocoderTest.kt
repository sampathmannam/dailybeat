package com.dailybeat.app.geo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.GeocodeCache
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
    fun `deeply nested small provider response cannot overflow the parser`() = runBlocking {
        server.enqueue(MockResponse().setBody(
            "{\"value\":" + "[".repeat(20_000) + "0" + "]".repeat(20_000) + "}",
        ))

        assertEquals("Unnamed place", geocoder.resolve(lat, lon).address)
        assertEquals(1, server.requestCount)
        assertNull(db.geocodes().get(server.url("/reverse").toString() + ":v3:11.4557,78.1856"))
    }

    @Test
    fun `quoted braces and escaped quotes in venue names remain usable`() = runBlocking {
        val name = """Shelf "[{}]" \ {Room}"""
        server.enqueue(MockResponse().setBody(JSONObject().apply {
            put("lat", lat.toString())
            put("lon", lon.toString())
            put("category", "shop")
            put("name", name)
            put("display_name", "$name, Example Road")
        }.toString()))

        assertEquals("Near $name", geocoder.resolve(lat, lon).name)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `JSON preflight accepts the depth boundary and rejects the next level`() {
        fun nested(arrays: Int) = "{\"value\":" + "[".repeat(arrays) + "0" + "]".repeat(arrays) + "}"
        assertTrue(isBoundedGeocoderJson(nested(63)))
        assertFalse(isBoundedGeocoderJson(nested(64)))
        assertTrue(isBoundedGeocoderJson(JSONObject().put("name", "[{}]".repeat(100)).toString()))
    }

    @Test
    fun `JSON preflight rejects malformed or lenient syntax before recursive parsing`() {
        listOf(
            "{unquoted: []}", "{'name': 'Shop'}", "{/* comment */\"name\": \"Shop\"}",
            "{\"name\": \"unterminated}", "{\"value\": [}", "{} {}", "[]", "null",
        ).forEach { payload -> assertFalse(payload, isBoundedGeocoderJson(payload)) }
        assertTrue(isBoundedGeocoderJson("{\"value\":[true,false,null,1.5,{},[]]}"))
    }

    @Test
    fun `request throttle spaces requests using only the injected monotonic clock`() = runBlocking {
        val clock = ArrayDeque(listOf(1_000L, 1_000L, 1_300L, 2_100L, 4_000L, 4_000L))
        val delays = mutableListOf<Long>()
        val throttle = GeocoderRequestThrottle(1_100L, { clock.removeFirst() }, { delays += it })

        repeat(3) { throttle.awaitTurn() }

        assertEquals(listOf(800L), delays)
        assertTrue(clock.isEmpty())
    }

    @Test
    fun `request throttle bounds rollback negative and overflow clock values`() = runBlocking {
        var now = 10_000L
        val delays = mutableListOf<Long>()
        val throttle = GeocoderRequestThrottle(1_100L, { now }, { delays += it })
        throttle.awaitTurn()
        for (time in listOf(Long.MIN_VALUE, Long.MAX_VALUE, 0L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            now = time
            throttle.awaitTurn()
        }

        assertEquals(listOf(1_100L, 1_100L, 1_100L, 1_100L), delays)
    }

    @Test
    fun `request throttle bounds invalid intervals without delaying the first request`() = runBlocking {
        val delays = mutableListOf<Long>()
        val excessive = GeocoderRequestThrottle(Long.MAX_VALUE, { 0L }, { delays += it })
        excessive.awaitTurn()
        assertTrue(delays.isEmpty())
        excessive.awaitTurn()
        assertEquals(listOf(60_000L), delays)

        val disabled = GeocoderRequestThrottle(Long.MIN_VALUE, { Long.MIN_VALUE }, { delays += it })
        repeat(2) { disabled.awaitTurn() }
        assertEquals(listOf(60_000L), delays)
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

    @Test
    fun `cancelling a stalled lookup releases capture promptly without a second request or cache`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val lookup = async(Dispatchers.IO) { geocoder.resolve(lat, lon) }
        try {
            assertTrue(server.takeRequest(5, TimeUnit.SECONDS) != null)
            // A synchronous execute call previously held the capture lock for its full timeout.
            withTimeout(2_000L) { lookup.cancelAndJoin() }
            assertTrue(lookup.isCancelled)
            assertEquals(1, server.requestCount)
            assertNull(db.geocodes().get(server.url("/reverse").toString() + ":v3:11.4557,78.1856"))
        } finally {
            lookup.cancelAndJoin()
        }
    }

    @Test
    fun `consent withdrawn while a provider replies discards the late enrichment`() = runBlocking {
        val allowed = AtomicBoolean(true)
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                allowed.set(false)
                return MockResponse().setBody(
                    """{"lat":"11.4557","lon":"78.1856","category":"shop","name":"Late Shop"}""",
                )
            }
        }
        val gated = OsmGeocoder(db.geocodes(), server.url("/reverse").toString(),
            permitsLookup = { _, _ -> allowed.get() }, minimumRequestIntervalMs = 0)

        val result = gated.resolve(lat, lon)

        assertNull(result.name)
        assertEquals("Unnamed place", result.address)
        assertEquals(1, server.requestCount)
        assertNull(db.geocodes().get(server.url("/reverse").toString() + ":v3:11.4557,78.1856"))
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
