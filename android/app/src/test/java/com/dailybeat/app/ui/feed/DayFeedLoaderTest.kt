package com.dailybeat.app.ui.feed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.LocationVisit
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DayFeedLoaderTest {
    private lateinit var db: DailyBeatDb
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DailyBeatDb::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }
    @Test fun `older pages include saved history and clip overnight stays across DST`() = runBlocking {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2025,3,9)
        db.diaries().upsert(DiaryEntry(date.toString(),"Retained older diary",1))
        val start = date.minusDays(1).atTime(23,30).atZone(zone).toInstant().toEpochMilli()
        val end = date.atTime(3,30).atZone(zone).toInstant().toEpochMilli()
        db.visits().insert(LocationVisit(startMs=start,endMs=end,latitude=40.7,longitude=-74.0,placeName="Saved place"))
        val days = DayFeedLoader(db).load(date,2,zone)
        assertEquals(listOf(date,date.minusDays(1)),days.map { it.date })
        assertEquals(150L, days.first().stays.single().durationMinutes)
        assertEquals(29L, days.last().stays.single().durationMinutes) // Inclusive millisecond end of previous day.
        assertTrue(DayFeedLoader(db).load(date.plusYears(1),30,zone).isEmpty())
    }
}
