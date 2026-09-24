package com.dailybeat.app.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.*
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.LocationBreadcrumb
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CaptureProcessorTest {
    private lateinit var db: DailyBeatDb
    private val start = 1_757_000_000_000L
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DailyBeatDb::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    private fun processor() = CaptureProcessor(db, OsmGeocoder(db.geocodes()))
    private fun fix(minutes: Long, lat: Double = 11.4557) = CaptureFix(start + minutes*60_000, lat, 78.1856, 20f, false)
    @Test fun `coarse route point cannot end a precise stop through durable processing`() = runBlocking {
        processor().enqueue(listOf(fix(0), fix(5), fix(9, 11.4600).copy(accuracyM = 220f)))
        processor().drain()
        assertTrue(db.visits().all().isEmpty())
        assertEquals(fix(5).timestampMs, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.lastSampleMs)
        assertTrue(db.breadcrumbs().all().any { it.quality == "approximate" })
        processor().enqueue(listOf(fix(10), fix(11, 11.4600)))
        processor().drain()
        val stay = db.visits().all().single()
        assertEquals(start, stay.startMs)
        assertEquals(fix(10).timestampMs, stay.endMs)
    }

    @Test fun `arrival candidate survives processor replacement and retains first arrival time`() = runBlocking {
        val arrival = 11.4700
        processor().enqueue(listOf(fix(0), fix(5), fix(10), fix(12, 11.4600), fix(15, arrival)))
        processor().drain()
        assertTrue(BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.inTransit)
        processor().enqueue(listOf(fix(19, arrival), fix(23, arrival)))
        processor().drain()
        val state = BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!
        assertFalse(state.inTransit)
        assertEquals(fix(15).timestampMs, state.dwellStartMs)
        val transit = db.visits().all().single { it.visitType == "transit" }
        assertEquals(fix(10).timestampMs, transit.startMs)
        assertEquals(fix(15).timestampMs, transit.endMs)
        processor().finish()
        val destination = db.visits().all().filter { it.visitType == "dwell" }.last()
        assertEquals(fix(15).timestampMs, destination.startMs)
        assertEquals(fix(23).timestampMs, destination.endMs)
    }

    @Test fun `checkpoint codec preserves loop displacement and supports old payloads`() {
        val checkpoint = BufferedCheckpoint()
        checkpoint.save(VisitTrackerState(11.4557, 78.1856, start + 20 * 60_000, start + 22 * 60_000,
            start, 11.4557, 78.1856, 11.4557, 78.1856, true, maxTransitDisplacementM = 1600.0))
        val encoded = checkpoint.encode()
        assertEquals(1600.0, BufferedCheckpoint(encoded).load()!!.maxTransitDisplacementM, 0.0)
        val legacy = org.json.JSONObject(encoded).apply { remove("maxTransitDisplacementM") }.toString()
        assertEquals(0.0, BufferedCheckpoint(legacy).load()!!.maxTransitDisplacementM, 0.0)
        val corrupt = org.json.JSONObject(encoded).apply { put("maxTransitDisplacementM", -1.0) }.toString()
        assertNull(BufferedCheckpoint(corrupt).load())
    }

    @Test fun `short suspension at the same place preserves a break after restart`() = runBlocking {
        processor().enqueue(listOf(fix(0), fix(5), fix(10)))
        processor().drain()
        processor().suspendCapture()
        processor().enqueue(listOf(fix(11), fix(16), fix(20)))
        processor().finish()
        val stays = db.visits().all().sortedBy { it.startMs }
        assertEquals(listOf(start, fix(11).timestampMs), stays.map { it.startMs })
        assertEquals(listOf(fix(10).timestampMs, fix(20).timestampMs), stays.map { it.endMs })
        assertTrue(stays.all { it.visitType == "dwell" })
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
    }

    @Test fun `interrupted transaction retains fix and replay creates exactly one visit and index`() = runBlocking {
        db.captureJournal().enqueue(listOf(fix(0), fix(10)))
        processor().drain()
        val checkpoint = db.captureJournal().checkpoint()
        db.captureJournal().enqueue(listOf(fix(11, 11.4600)))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_index BEFORE INSERT ON events BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END")
        assertTrue(runCatching { processor().drain() }.isFailure)
        assertEquals(0, db.visits().all().size)
        assertEquals(checkpoint, db.captureJournal().checkpoint())
        assertEquals(1, db.captureJournal().pending().size)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_index")
        processor().drain() // New processor, as after process death.
        db.captureJournal().enqueue(listOf(fix(11, 11.4600)))
        processor().drain() // Duplicate platform callback.
        assertEquals(1, db.visits().all().size)
        assertEquals(1, db.events().all().size)
        assertEquals(3, db.breadcrumbs().all().size)
        assertTrue(db.captureJournal().pending().isEmpty())
    }
    @Test fun `sleep cannot discard short stay and confirmed checkpoint survives process replacement`() = runBlocking {
        db.captureJournal().enqueue(listOf(fix(0), fix(5)))
        processor().drain()
        assertFalse(AdaptiveCapturePolicy.hasConfirmedStay(BufferedCheckpoint(db.captureJournal().checkpoint()).load()))
        db.captureJournal().enqueue(listOf(fix(10)))
        processor().drain()
        assertTrue(AdaptiveCapturePolicy.hasConfirmedStay(BufferedCheckpoint(db.captureJournal().checkpoint()).load()))
        processor().suspendCapture()
        // No flush at battery sleep. On waking, only observed time is attributed to the stay.
        db.captureJournal().enqueue(listOf(fix(60, 11.4600)))
        processor().drain()
        assertEquals(start + 10*60_000, db.visits().all().first { it.visitType == "dwell" }.endMs)
        assertTrue(db.visits().all().none { it.visitType == "transit" })
    }
    @Test fun `failed privacy lookup prevents network and leaves fix available for retry`() = runBlocking {
        db.captureJournal().enqueue(listOf(fix(0), fix(10)))
        processor().drain()
        db.openHelper.writableDatabase.execSQL("DROP TABLE places")
        var calls = 0
        val network = object : OsmGeocoder(db.geocodes()) {
            override suspend fun resolve(latitude: Double, longitude: Double): com.dailybeat.app.geo.ResolvedPlace {
                calls++
                return com.dailybeat.app.geo.ResolvedPlace("Unexpected", "Unexpected")
            }
        }
        db.captureJournal().enqueue(listOf(fix(11,11.4600)))
        assertTrue(runCatching { CaptureProcessor(db,network).drain() }.isFailure)
        assertEquals(0,calls)
        assertEquals(1,db.captureJournal().pending().size)
        assertTrue(db.visits().all().isEmpty())
    }
    @Test fun `rejected fixes update health without becoming stored history`() = runBlocking {
        db.captureJournal().enqueue(listOf(fix(0).copy(accuracyM = 10_000f)))
        var rejections = 0
        processor().drain(onRejected = { _, _ -> rejections++ })
        assertEquals(1, rejections)
        assertTrue(db.breadcrumbs().all().isEmpty())
        assertTrue(db.captureJournal().pending().isEmpty())
    }
    @Test fun `invalid numerical fix cannot abort storage of the valid fixes in its platform batch`() = runBlocking {
        val rejected = mutableListOf<String>()
        val processor = processor()
        processor.enqueue(listOf(
            fix(0).copy(latitude = Double.NaN),
            fix(1).copy(accuracyM = Float.NaN),
            fix(2).copy(longitude = Double.POSITIVE_INFINITY),
            fix(3),
            fix(4).copy(timestampMs = 0),
        ), onRejected = { _, reason -> rejected += reason })
        processor.drain()
        assertEquals(listOf("invalid-coordinate", "missing-accuracy", "invalid-coordinate", "invalid-time"), rejected)
        assertEquals(listOf(fix(3).timestampMs), db.breadcrumbs().all().map { it.timestampMs })
        assertTrue(db.captureJournal().pending().isEmpty())
    }
    @Test fun `legacy queued future fix is rejected and later normal capture remains usable`() = runBlocking {
        val processor = CaptureProcessor(db, OsmGeocoder(db.geocodes()), clock = { start + 60 * 60_000 })
        db.captureJournal().enqueue(listOf(fix(0), fix(1).copy(timestampMs = Long.MAX_VALUE)))
        val rejected = mutableListOf<String>()
        processor.drain(onRejected = { _, reason -> rejected += reason })
        processor.enqueue(listOf(fix(10)))
        processor.drain()
        assertEquals(listOf("future-time"), rejected)
        assertEquals(2, db.breadcrumbs().all().size)
        assertEquals(fix(10).timestampMs, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.lastSampleMs)
    }
    @Test fun `legacy future checkpoint and route point cannot permanently disable capture`() = runBlocking {
        val memory = BufferedCheckpoint()
        memory.save(VisitTrackerState(11.4557, 78.1856, start, Long.MAX_VALUE, 0, null, null, null, null, false))
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = memory.encode()))
        db.breadcrumbs().insert(LocationBreadcrumb(timestampMs = Long.MAX_VALUE, latitude = 11.4557,
            longitude = 78.1856, accuracyM = 20f, quality = "good"))
        val processor = CaptureProcessor(db, OsmGeocoder(db.geocodes()), clock = { start + 60 * 60_000 })
        processor.enqueue(listOf(fix(0), fix(10)))
        processor.drain()
        assertEquals(3, db.breadcrumbs().all().size) // Existing history is preserved.
        assertEquals(start, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.dwellStartMs)
        assertEquals(fix(10).timestampMs, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.lastSampleMs)
        assertTrue(db.visits().all().isEmpty())
    }
    @Test fun `corrupt checkpoints recover from the next fix without inventing a prior stay`() = runBlocking {
        val malformed = listOf(
            "{broken",
            "{\"lastSampleMs\":\"not a timestamp\"}",
            "{\"lastSampleMs\":$start,\"dwellStartMs\":1,\"transitStartMs\":0,\"dwellLat\":11.4,\"inTransit\":false}",
        )
        malformed.forEachIndexed { index, payload ->
            db.captureJournal().checkpoint(CaptureCheckpoint(payload = payload))
            val fix = fix(index.toLong() * 10)
            processor().enqueue(listOf(fix))
            processor().drain()
            assertEquals(fix.timestampMs, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.dwellStartMs)
            assertTrue(db.captureJournal().pending().isEmpty())
        }
        assertEquals(3, db.breadcrumbs().all().size)
        assertTrue(db.visits().all().isEmpty())
    }
    @Test fun `stopping capture drains queued departures without geocoding and finalizes exactly once`() = runBlocking {
        var networkCalls = 0
        val geocoder = object : OsmGeocoder(db.geocodes()) {
            override suspend fun resolve(latitude: Double, longitude: Double): com.dailybeat.app.geo.ResolvedPlace {
                networkCalls++
                throw AssertionError("Capture teardown must not geocode queued fixes")
            }
        }
        val processor = CaptureProcessor(db, geocoder)
        processor.enqueue(listOf(fix(0), fix(10), fix(11, 11.4600)))
        processor.finish()
        processor.finish()
        assertEquals(0, networkCalls)
        assertEquals(1, db.visits().all().size)
        assertEquals(start + 10 * 60_000, db.visits().all().single().endMs)
        assertEquals(1, db.events().all().size)
        assertTrue(db.captureJournal().pending().isEmpty())
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
    }
    @Test fun `failed stop recovery finalizes offline and a quick resume cannot bridge the pause`() = runBlocking {
        var networkCalls = 0
        val geocoder = object : OsmGeocoder(db.geocodes()) {
            override suspend fun resolve(latitude: Double, longitude: Double): com.dailybeat.app.geo.ResolvedPlace {
                networkCalls++
                throw AssertionError("Privacy-stop recovery must not perform a lookup")
            }
        }
        val processor = CaptureProcessor(db, geocoder)
        processor.enqueue(listOf(fix(0), fix(5), fix(10)))
        processor.drain()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_stop BEFORE INSERT ON events BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END")
        assertTrue(runCatching { processor.finish() }.isFailure)
        assertNotNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
        assertTrue(db.visits().all().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_stop")

        processor.recover(captureAllowed = false)
        processor.recover(captureAllowed = false) // A duplicate background retry is harmless.
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
        assertEquals(1, db.visits().all().size)

        // Less than the ten-minute gap guard: only explicit finalization protects this pause.
        processor.enqueue(listOf(fix(12), fix(17), fix(22)))
        processor.drain()
        processor.finish()
        val stays = db.visits().all().sortedBy { it.startMs }
        assertEquals(listOf(start, fix(12).timestampMs), stays.map { it.startMs })
        assertEquals(listOf(fix(10).timestampMs, fix(22).timestampMs), stays.map { it.endMs })
        assertEquals(2, db.events().all().size)
        assertEquals(0, networkCalls)
    }

    @Test fun `unavailable capture recovery keeps queued observations and health callbacks but closes checkpoint`() = runBlocking {
        val processor = processor()
        processor.enqueue(listOf(fix(0), fix(5), fix(10)))
        val observed = mutableListOf<Long>()

        processor.recover(captureAllowed = false, onAccepted = { sample, _ -> observed += sample.timestampMs })

        assertEquals(listOf(fix(0).timestampMs, fix(5).timestampMs, fix(10).timestampMs), observed)
        assertEquals(3, db.breadcrumbs().all().size)
        assertEquals(fix(10).timestampMs, db.visits().all().single().endMs)
        assertTrue(db.captureJournal().pending().isEmpty())
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
    }

    @Test fun `failed final stop insert cannot bridge a quick re-enable before recovery`() = runBlocking {
        var networkCalls = 0
        val geocoder = object : OsmGeocoder(db.geocodes()) {
            override suspend fun resolve(latitude: Double, longitude: Double): com.dailybeat.app.geo.ResolvedPlace {
                networkCalls++
                throw AssertionError("A retained privacy boundary must finalize offline")
            }
        }
        val processor = CaptureProcessor(db, geocoder)
        processor.enqueue(listOf(fix(0), fix(5), fix(10)))
        processor.drain()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_stop_visit BEFORE INSERT ON location_visits BEGIN SELECT RAISE(ABORT, 'simulated disk failure'); END")
        assertTrue(runCatching { processor.finish() }.isFailure)
        assertTrue(BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.suspended)
        assertTrue(db.visits().all().isEmpty())
        assertTrue(db.events().all().isEmpty())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_stop_visit")

        // A new processor models process replacement. Recovery already sees capture enabled.
        val restarted = CaptureProcessor(db, geocoder)
        restarted.recover(captureAllowed = true)
        restarted.enqueue(listOf(fix(12), fix(17), fix(22)))
        restarted.drain()
        restarted.finish()
        restarted.finish()

        val stays = db.visits().all().sortedBy { it.startMs }
        assertEquals(listOf(start, fix(12).timestampMs), stays.map { it.startMs })
        assertEquals(listOf(fix(10).timestampMs, fix(22).timestampMs), stays.map { it.endMs })
        assertEquals(2, db.events().all().size)
        assertEquals(0, networkCalls)
        assertTrue(db.captureJournal().pending().isEmpty())
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
    }

    @Test fun `stop marker does not split already queued observations from an open stay`() = runBlocking {
        val processor = processor()
        processor.enqueue(listOf(fix(0), fix(5)))
        processor.drain()
        processor.enqueue(listOf(fix(10)))

        processor.finish()

        val stay = db.visits().all().single()
        assertEquals(start, stay.startMs)
        assertEquals(fix(10).timestampMs, stay.endMs)
        assertEquals(1, db.events().all().size)
        assertTrue(db.captureJournal().pending().isEmpty())
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint()).load())
    }

    @Test fun `active capture recovery preserves the open observed interval`() = runBlocking {
        val processor = processor()
        processor.enqueue(listOf(fix(0), fix(5), fix(10)))

        processor.recover(captureAllowed = true)

        assertTrue(db.visits().all().isEmpty())
        assertEquals(start, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.dwellStartMs)
        assertEquals(fix(10).timestampMs, BufferedCheckpoint(db.captureJournal().checkpoint()).load()!!.lastSampleMs)
    }

    @Test fun `foreground only and watcher absent configurations never sleep`() {
        assertFalse(AdaptiveCapturePolicy.canSleep(false, false))
        assertFalse(AdaptiveCapturePolicy.canSleep(false, true))
        assertFalse(AdaptiveCapturePolicy.canSleep(true, false))
        assertTrue(AdaptiveCapturePolicy.canSleep(true, true))
    }
    @Test fun `captured transit event keeps its resolved place`() {
        val event = capturedVisitEvent(
            LocationVisit(
                startMs = start,
                endMs = start + 60_000,
                latitude = 11.4557,
                longitude = 78.1856,
                address = "Paramathi Road, Namakkal",
                visitType = "transit",
            ),
        )
        assertEquals("Travel · Paramathi Road, Namakkal", event.rawText)
        assertEquals("Paramathi Road, Namakkal", event.placeName)
    }
    @Test fun `captured transit event does not present an unresolved placeholder as a place`() {
        val event = capturedVisitEvent(
            LocationVisit(
                startMs = start,
                endMs = start + 60_000,
                latitude = 11.4557,
                longitude = 78.1856,
                address = "Unnamed place",
                visitType = "transit",
            ),
        )
        assertEquals("Travel recorded", event.rawText)
        assertNull(event.placeName)
    }

    @Test fun `captured stay uses its address when its name is an old placeholder`() {
        val event = capturedVisitEvent(
            LocationVisit(
                startMs = start, endMs = start + 60_000, latitude = 11.4557, longitude = 78.1856,
                placeName = "Unnamed place", address = "Paramathi Road, Namakkal",
            ),
        )

        assertEquals("Stay at Paramathi Road, Namakkal", event.rawText)
        assertEquals("Paramathi Road, Namakkal", event.placeName)
    }

    @Test fun `captured unknown stay does not persist a placeholder as its name`() {
        val event = capturedVisitEvent(
            LocationVisit(
                startMs = start, endMs = start + 60_000, latitude = 11.4557, longitude = 78.1856,
                placeName = "Unnamed place", address = "Unnamed place",
            ),
        )

        assertEquals("Stay recorded", event.rawText)
        assertNull(event.placeName)
    }
}
