package com.dailybeat.app.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.repo.DiaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeekExportSelectionTest {
    @Test fun weekIncludesItsBoundariesButNotOlderFutureOrClearedEntries() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DailyBeatDb::class.java).build()
        try {
            val repo = DiaryRepository(db.diaries())
            val end = LocalDate.of(2099, 1, 3)
            listOf(-30L, -7L, -6L, -3L, 0L, 1L).forEach { repo.saveForDate(end.plusDays(it), "Synthetic $it") }
            repo.saveForDate(end.minusDays(3), " ")
            assertEquals(listOf("2099-01-03", "2098-12-28"), repo.weekEnding(end).map { it.dateKey })
            assertEquals(6, db.diaries().all().size)
        } finally { db.close() }
    }
}
