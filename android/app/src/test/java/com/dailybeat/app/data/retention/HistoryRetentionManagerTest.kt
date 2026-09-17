package com.dailybeat.app.data.retention

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.BeatReview
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.DiaryRevision
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.VisitCorrection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryRetentionManagerTest {
    private lateinit var db: DailyBeatDb

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DailyBeatDb::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `thirty day policy removes complete older days and their history only`() = runBlocking {
        val oldMs = Instant.parse("2026-08-18T12:00:00Z").toEpochMilli()
        val keptMs = Instant.parse("2026-08-19T00:00:00Z").toEpochMilli()
        db.events().insert(Event(id = 1, timestamp = oldMs, type = "manual", rawText = "old"))
        db.events().insert(Event(id = 2, timestamp = keptMs, type = "manual", rawText = "kept"))
        db.visits().insert(LocationVisit(id = 1, startMs = oldMs, endMs = oldMs, latitude = 1.0, longitude = 1.0))
        // Even a recent correction must not outlive the older visit it describes.
        db.visits().insertCorrection(VisitCorrection(id = 1, visitId = 1, correctedAt = keptMs, field = "hidden", oldValue = "false", newValue = "true"))
        db.breadcrumbs().insert(LocationBreadcrumb(id = 1, timestampMs = oldMs, latitude = 1.0, longitude = 1.0, accuracyM = 5f))
        db.diaries().upsert(DiaryEntry("2026-08-18", "old", oldMs))
        db.diaries().upsert(DiaryEntry("2026-08-19", "kept", keptMs))
        db.diaries().insertRevision(DiaryRevision(id = 1, dateKey = "2026-08-18", text = "old", createdAt = oldMs, reason = "test"))
        db.beatReviews().upsert(BeatReview("2026-08-18", updatedAt = oldMs))

        val result = HistoryRetentionManager(
            db = db,
            clock = { Instant.parse("2026-09-17T12:00:00Z").toEpochMilli() },
            zoneId = ZoneOffset.UTC,
        ).prune(30)

        assertEquals("2026-08-19", result.cutoffDate.toString())
        assertEquals(listOf(2L), db.events().all().map { it.id })
        assertEquals(listOf("2026-08-19"), db.diaries().all().map { it.dateKey })
        assertEquals(emptyList<Any>(), db.visits().all())
        assertEquals(emptyList<Any>(), db.visits().allCorrections())
        assertEquals(emptyList<Any>(), db.breadcrumbs().all())
        assertEquals(emptyList<Any>(), db.diaries().allRevisions())
        assertEquals(emptyList<Any>(), db.beatReviews().all())
    }
}
