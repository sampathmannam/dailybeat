package com.dailybeat.app.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalBackupStoreTest {

    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var store: LocalBackupStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        store = LocalBackupStore(db, settings) { 9_999L }
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `snapshot round trip preserves records and non-secret settings`() = runBlocking {
        seedOriginalData()

        val snapshot = store.createSnapshot()

        assertEquals(9_999L, snapshot.createdAtMs)
        assertEquals(listOf(1L), snapshot.events.map { it.id })
        assertEquals("Sampath", snapshot.settings.officerName)

        db.events().deleteAll()
        db.events().insert(Event(id = 2, timestamp = 8_000L, type = "manual", rawText = "replacement"))
        settings.setOfficerName("Changed")

        store.restore(snapshot)

        assertEquals(listOf(1L), db.events().all().map { it.id })
        assertEquals(listOf(2L), db.places().all().map { it.id })
        assertEquals(listOf("2026-08-31"), db.diaries().all().map { it.dateKey })
        assertEquals(listOf(3L), db.visits().all().map { it.id })
        assertEquals("Sampath", settings.get().officerName)
    }

    @Test
    fun `failed restore rolls back deletions`() = runBlocking {
        seedOriginalData()
        val invalid = store.createSnapshot().copy(
            events = listOf(
                Event(id = 7, timestamp = 1L, type = "manual", rawText = "one"),
                Event(id = 7, timestamp = 2L, type = "manual", rawText = "duplicate"),
            ),
        )

        assertThrows(Exception::class.java) {
            runBlocking { store.restore(invalid) }
        }

        assertEquals(listOf(1L), db.events().all().map { it.id })
        assertEquals("Sampath", settings.get().officerName)
    }

    private suspend fun seedOriginalData() {
        db.events().insert(Event(id = 1, timestamp = 1_000L, type = "manual", rawText = "original"))
        db.places().insert(Place(id = 2, name = "HQ", latitude = 17.4, longitude = 78.5))
        db.diaries().upsert(DiaryEntry("2026-08-31", "Original diary", 2_000L))
        db.visits().insert(LocationVisit(id = 3, startMs = 3_000L, endMs = 4_000L, latitude = 17.4, longitude = 78.5))
        settings.setOfficerName("Sampath")
        settings.setGpsEnabled(false)
    }
}
