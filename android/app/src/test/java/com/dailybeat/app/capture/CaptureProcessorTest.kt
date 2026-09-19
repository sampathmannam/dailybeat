package com.dailybeat.app.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.*
import com.dailybeat.app.geo.OsmGeocoder
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
    @Test fun `foreground only and watcher absent configurations never sleep`() {
        assertFalse(AdaptiveCapturePolicy.canSleep(false, false))
        assertFalse(AdaptiveCapturePolicy.canSleep(false, true))
        assertFalse(AdaptiveCapturePolicy.canSleep(true, false))
        assertTrue(AdaptiveCapturePolicy.canSleep(true, true))
    }
}
