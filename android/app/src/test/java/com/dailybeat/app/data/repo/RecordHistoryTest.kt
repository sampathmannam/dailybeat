package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.LocationVisit
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordHistoryTest {
    private lateinit var db: DailyBeatDb

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `diary checkpoint is deduplicated and can be restored without losing current draft`() = runBlocking {
        val date = LocalDate.of(2026, 9, 17)
        val repository = DiaryRepository(db.diaries())
        repository.saveForDate(date, "Original")
        repository.checkpointForDate(date, "Before editing")
        repository.checkpointForDate(date, "Duplicate checkpoint")
        repository.saveForDate(date, "Current")

        val original = repository.observeRevisions(date).first().single()
        repository.restoreRevision(date, original)

        assertEquals("Original", repository.textForDate(date))
        val history = repository.observeRevisions(date).first()
        assertEquals(2, history.size)
        assertTrue(history.any { it.text == "Current" })
    }

    @Test
    fun `renaming and hiding a visit creates immutable correction records`() = runBlocking {
        val visit = LocationVisit(
            id = 8,
            startMs = 1_000,
            endMs = 2_000,
            latitude = 11.4,
            longitude = 78.1,
            placeName = "Old name",
        )
        db.visits().insert(visit)
        val repository = VisitRepository(db.visits(), db)

        repository.rename(visit, "New name")
        repository.setHidden(visit.copy(placeName = "New name", manuallyEdited = true), true)

        val corrections = db.visits().allCorrections()
        assertEquals(listOf("placeName", "hidden"), corrections.map { it.field })
        assertEquals("Old name", corrections[0].oldValue)
        assertEquals("New name", corrections[0].newValue)
        assertEquals("false", corrections[1].oldValue)
        assertEquals("true", corrections[1].newValue)
    }
}
