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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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

    @Test
    fun saveTodayBoundsAndSanitizesUntrustedText() = runBlocking {
        repository.saveToday("A\u0000" + "b".repeat(60_000))

        val saved = repository.todayText().orEmpty()
        assertEquals(50_000, saved.length)
        assertFalse(saved.contains('\u0000'))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaceRepositoryTest {

    private lateinit var db: DailyBeatDb
    private lateinit var repository: PlaceRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaceRepository(db.places())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addBoundsNameAndRecognitionRadius() = runBlocking {
        repository.add("A\u0000" + "b".repeat(200), 12.9, 77.6, radiusM = 1)

        val saved = repository.all().single()
        assertEquals(120, saved.name.length)
        assertFalse(saved.name.contains('\u0000'))
        assertEquals(25, saved.radiusM)
    }

    @Test
    fun addRejectsCoordinatesOutsideEarth() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.add("Invalid", 91.0, 77.6) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.add("Invalid", 12.9, Double.NaN) }
        }
    }

    @Test
    fun privacyChangePreservesOtherSavedPlaceFields() = runBlocking {
        repository.add("Home", 12.9, 77.6, radiusM = 350)
        val original = repository.all().single()
        repository.setPrivate(original, true)
        assertEquals(original.copy(isPrivate = true), repository.all().single())
    }

    @Test
    fun stalePrivacyToggleCannotOverwriteNewerNameOrCoordinates() = runBlocking {
        repository.add("Office", 12.9, 77.6)
        val stale = repository.all().single()
        val current = stale.copy(name = "Clinic", latitude = 13.1, radiusM = 250)
        db.places().update(current)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.setPrivate(stale, true) }
        }
        assertEquals(current, repository.all().single())
    }

    @Test
    fun staleDeleteCannotRemoveANewerPrivacyChoice() = runBlocking {
        repository.add("Home", 12.9, 77.6)
        val stale = repository.all().single()
        repository.setPrivate(stale, true)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.delete(stale) }
        }
        val current = repository.all().single()
        assertTrue(current.isPrivate)
        repository.delete(current)
        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun staleDeleteCannotRemoveReplacementReusingItsIdentifier() = runBlocking {
        repository.add("Old place", 12.9, 77.6)
        val stale = repository.all().single()
        db.places().deleteAll()
        val replacement = stale.copy(name = "Restored place", longitude = 78.2, isPrivate = true)
        db.places().insert(replacement)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.delete(stale) }
        }
        assertEquals(replacement, repository.all().single())
    }
}
