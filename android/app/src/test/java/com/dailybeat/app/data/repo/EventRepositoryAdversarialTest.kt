package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.StructuredEvent
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

    @Test
    fun addStructuredEvent_boundsCloudControlledFields() = runBlocking {
        repo.addStructuredEvent(
            StructuredEvent(
                rawText = "x".repeat(20_000),
                placeName = "p".repeat(2_000),
                peopleMentioned = "n".repeat(2_000),
                caseNumbers = "c".repeat(2_000),
            ),
            type = "v".repeat(100),
        )

        val event = repo.eventsForDate(com.dailybeat.app.util.DateKeys.today()).single()
        assertEquals(8_000, event.rawText.length)
        assertEquals(500, event.placeName?.length)
        assertEquals(1_000, event.peopleMentioned?.length)
        assertEquals(1_000, event.caseNumbers?.length)
        assertEquals(32, event.type.length)
    }
}
