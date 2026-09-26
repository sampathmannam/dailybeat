package com.dailybeat.app.data.retention

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.capture.BufferedCheckpoint
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.capture.VisitTrackerState
import com.dailybeat.app.data.db.CaptureCheckpoint
import com.dailybeat.app.data.db.CaptureFix
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.repo.DiaryRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Frozen synthetic fixtures for the second autoresearch pass; no device/user data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RetentionResearchTest {
    private lateinit var db: DailyBeatDb
    private val now = Instant.parse("2026-09-17T12:00:00Z").toEpochMilli()
    private val old = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli()
    private val recent = Instant.parse("2026-09-17T10:00:00Z").toEpochMilli()
    private fun manager() = HistoryRetentionManager(db, clock = { now }, zoneId = ZoneOffset.UTC)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            DailyBeatDb::class.java).allowMainThreadQueries().build()
    }
    @After fun close() = db.close()

    @Test fun delayedDiarySaveCannotRecreateHistoryDeletedByRetention() = runBlocking {
        val date = LocalDate.parse("2026-08-18")
        db.diaries().upsert(DiaryEntry(date.toString(), "Synthetic old text", old))
        val openedGeneration = CaptureStorageGate.dataGeneration.get()
        manager().prune(30)

        // Same generation gate used by a delayed editor save or report-generation result.
        val save = runCatching {
            CaptureStorageGate.writeIfCurrent(openedGeneration) {
                DiaryRepository(db.diaries()).saveForDate(date, "Synthetic stale draft")
            }
        }
        assertTrue("A stale save must not resurrect the pruned diary", save.isFailure)
        assertNull(db.diaries().forDate(date.toString()))
    }

    @Test fun committedDeletionInvalidatesReadersOnceWithoutRestartingGps() = runBlocking {
        db.diaries().upsert(DiaryEntry("2026-08-18", "Synthetic old text", old))
        val dataBefore = CaptureStorageGate.dataGeneration.get()
        val captureBefore = CaptureStorageGate.generation.get()
        val result = manager().prune(30)

        assertEquals(1, result.recordsDeleted)
        assertEquals(dataBefore + 1L, CaptureStorageGate.dataGeneration.get())
        assertEquals(dataBefore + 1L, CaptureStorageGate.dataChanges.value)
        assertEquals(captureBefore, CaptureStorageGate.generation.get())
        manager().prune(30)
        assertEquals("No-op retries must not keep invalidating editors", dataBefore + 1L,
            CaptureStorageGate.dataGeneration.get())
    }

    @Test fun noOpRetentionPreservesTheCurrentEditingGeneration() = runBlocking {
        db.diaries().upsert(DiaryEntry("2026-09-17", "Synthetic current text", recent))
        val generation = CaptureStorageGate.dataGeneration.get()
        assertEquals(0, manager().prune(30).recordsDeleted)
        assertEquals(generation, CaptureStorageGate.dataGeneration.get())
        CaptureStorageGate.writeIfCurrent(generation) {
            DiaryRepository(db.diaries()).saveForDate(LocalDate.parse("2026-09-17"), "Updated current text")
        }
        assertEquals("Updated current text", db.diaries().forDate("2026-09-17")!!.text)
    }

    @Test fun failedRetentionRollsBackWithoutInvalidatingReaders() = runBlocking {
        db.diaries().upsert(DiaryEntry("2026-08-18", "Synthetic old text", old))
        val generation = CaptureStorageGate.dataGeneration.get()
        failDiaryDeletion()
        assertTrue(runCatching { manager().prune(30) }.isFailure)
        assertNotNull(db.diaries().forDate("2026-08-18"))
        assertEquals(generation, CaptureStorageGate.dataGeneration.get())
    }

    @Test fun nestedRestorePruningDoesNotPublishBeforeItsOuterTransactionCommits() = runBlocking {
        db.diaries().upsert(DiaryEntry("2026-08-18", "Synthetic old text", old))
        val generation = CaptureStorageGate.dataGeneration.get()
        val result = runCatching {
            CaptureStorageGate.mutex.withLock {
                db.withTransaction {
                    manager().pruneInsideCaptureLock(30)
                    check(CaptureStorageGate.dataGeneration.get() == generation)
                    error("Synthetic restore rollback")
                }
            }
        }
        assertTrue(result.isFailure)
        assertNotNull(db.diaries().forDate("2026-08-18"))
        assertEquals(generation, CaptureStorageGate.dataGeneration.get())
    }

    @Test fun invalidCheckpointCoordinatesAreScrubbedEvenWhenTheCodecCannotRestoreIt() = runBlocking {
        // A partial coordinate pair makes this checkpoint unusable but still contains a location.
        val payload = checkpoint(old).let { org.json.JSONObject(it).apply { remove("dwellLon") }.toString() }
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = payload))
        assertNull(BufferedCheckpoint(payload, now).load())
        manager().prune(30)
        assertEquals("{}", db.captureJournal().checkpoint())
    }

    @Test fun futureCorruptCheckpointUsesTheSameClockAsRetentionAndIsScrubbed() = runBlocking {
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = checkpoint(now + 24 * 60 * 60_000L)))
        manager().prune(30)
        assertEquals("{}", db.captureJournal().checkpoint())
    }

    @Test fun expiredCheckpointLeavesAnEmptyMarkerRatherThanReopeningLegacyMigration() = runBlocking {
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = checkpoint(old)))
        manager().prune(30)
        // Service startup imports the legacy preferences checkpoint only if this row is absent.
        assertEquals("{}", db.captureJournal().checkpoint())
        assertNull(BufferedCheckpoint(db.captureJournal().checkpoint(), now).load())
    }

    @Test fun validRecentCheckpointAndQueuedObservationRemainIntact() = runBlocking {
        val payload = checkpoint(recent)
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = payload))
        val fix = CaptureFix(recent, 11.45, 78.18, 20f, false)
        db.captureJournal().enqueue(listOf(fix))
        manager().prune(30)
        assertEquals(payload, db.captureJournal().checkpoint())
        assertEquals(listOf(fix), db.captureJournal().pending())
    }

    @Test fun checkpointScrubbingRollsBackTogetherWithAFailedPrune() = runBlocking {
        val payload = checkpoint(old)
        db.captureJournal().checkpoint(CaptureCheckpoint(payload = payload))
        db.diaries().upsert(DiaryEntry("2026-08-18", "Synthetic old text", old))
        failDiaryDeletion()
        assertTrue(runCatching { manager().prune(30) }.isFailure)
        assertEquals(payload, db.captureJournal().checkpoint())
    }

    private fun checkpoint(time: Long): String = BufferedCheckpoint().apply {
        save(VisitTrackerState(11.45, 78.18, time - 60_000L, time, 0L,
            null, null, null, null, inTransit = false))
    }.encode()

    private fun failDiaryDeletion() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_retention BEFORE DELETE ON diaries BEGIN SELECT RAISE(ABORT, 'synthetic storage failure'); END",
        )
    }
}
