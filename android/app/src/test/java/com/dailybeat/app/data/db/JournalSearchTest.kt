package com.dailybeat.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JournalSearchTest {
    @Test fun searchFindsOldNotesDiariesAndVisiblePlacesWithLiteralWildcards() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DailyBeatDb::class.java).build()
        try {
            db.diaries().upsert(DiaryEntry("2020-01-01", "Warranty inspection", 1))
            db.events().insert(Event(timestamp = 2, type = "manual", rawText = "Inspection 20% complete"))
            db.visits().insert(LocationVisit(startMs = 3, endMs = 4, latitude = 1.0, longitude = 2.0, placeName = "Inspection office"))
            db.visits().insert(LocationVisit(startMs = 3, endMs = 4, latitude = 1.0, longitude = 2.0, placeName = "Inspection hidden", hidden = true))
            assertEquals(setOf("Note", "Place", "Diary"), db.journalSearch().search(JournalSearchDao.pattern("inspection")).map { it.kind }.toSet())
            assertEquals(1, db.journalSearch().search(JournalSearchDao.pattern("20%")).size)
            assertEquals(0, db.journalSearch().search(JournalSearchDao.pattern("_")).size)
        } finally { db.close() }
    }
    @Test fun timestampResultsUseDeviceZoneRatherThanUtc() {
        val hit = JournalSearchHit(null, java.time.Instant.parse("2026-09-15T21:00:00Z").toEpochMilli(), "note", "Note")
        assertEquals(LocalDate.of(2026, 9, 16), hit.date(ZoneId.of("Asia/Kolkata")))
    }
}
