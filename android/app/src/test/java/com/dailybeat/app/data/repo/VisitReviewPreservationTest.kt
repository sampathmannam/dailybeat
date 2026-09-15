package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.LocationVisit
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
class VisitReviewPreservationTest {
    @Test fun clippedUiEditsNeverOverwriteOvernightEvidenceOrConcurrentNameChanges() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DailyBeatDb::class.java).build()
        try {
            val day = LocalDate.of(2026, 9, 15)
            val midnight = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val original = LocationVisit(startMs = midnight - 3600000, endMs = midnight + 3600000,
                latitude = 1.0, longitude = 2.0, placeName = "Old")
            db.visits().insert(original)
            val repo = VisitRepository(db.visits())
            val clipped = repo.visitsForDate(day).single()
            assertEquals(midnight, clipped.startMs)
            repo.rename(clipped, "Corrected")
            val saved = db.visits().all().single()
            assertEquals(original.startMs, saved.startMs)
            assertEquals(original.endMs, saved.endMs)
            assertTrue(runCatching { repo.rename(clipped, "Stale overwrite") }.isFailure)
            repo.setHidden(clipped, true)
            assertEquals("Corrected", db.visits().all().single().placeName)
            assertEquals(original.startMs, db.visits().all().single().startMs)
        } finally { db.close() }
    }
}
