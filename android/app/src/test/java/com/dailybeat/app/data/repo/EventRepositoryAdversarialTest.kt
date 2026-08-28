package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventRepositoryAdversarialTest {

    private lateinit var db: DailyBeatDb
    private lateinit var repo: EventRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repo = EventRepository(db.events())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addManualEvent_truncatesExtremelyLongInput() = runBlocking {
        val longText = "x".repeat(20_000)
        repo.addManualEvent(longText)
        val events = repo.eventsForDate(com.dailybeat.app.util.DateKeys.today())
        assertEquals(8000, events.first().rawText.length)
    }

    @Test
    fun addManualEvent_ignoresBlankAndWhitespace() = runBlocking {
        repo.addManualEvent("   ")
        val count = repo.countToday()
        assertEquals(0, count)
    }

    @Test
    fun addMomentMarker_insertsMomentType() = runBlocking {
        repo.addMomentMarker()
        val event = repo.eventsForDate(com.dailybeat.app.util.DateKeys.today()).first()
        assertEquals("moment", event.type)
    }
}
