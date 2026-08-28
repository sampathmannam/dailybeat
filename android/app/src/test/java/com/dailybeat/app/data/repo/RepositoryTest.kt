package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EventRepositoryTest {

    private lateinit var db: DailyBeatDb
    private lateinit var repository: EventRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = EventRepository(db.events())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addManualEvent_persistsEvent() = runBlocking {
        repository.addManualEvent("Station visit")
        val events = repository.observeTodayEvents().first()
        assertEquals(1, events.size)
        assertEquals("Station visit", events.first().rawText)
    }

    @Test
    fun addManualEvent_ignoresBlank() = runBlocking {
        repository.addManualEvent("   ")
        val events = repository.observeTodayEvents().first()
        assertTrue(events.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiaryRepositoryTest {

    private lateinit var db: DailyBeatDb
    private lateinit var repository: DiaryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DiaryRepository(db.diaries())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun saveToday_persistsDiary() = runBlocking {
        repository.saveToday("Formal dairy entry.")
        assertEquals("Formal dairy entry.", repository.todayText())
    }

    @Test
    fun saveToday_ignoresBlank() = runBlocking {
        repository.saveToday("   ")
        val count = db.diaries().countNonEmpty()
        assertEquals(0, count)
    }
}
