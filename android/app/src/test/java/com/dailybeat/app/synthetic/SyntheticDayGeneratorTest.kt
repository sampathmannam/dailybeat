package com.dailybeat.app.synthetic

import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SyntheticDayGeneratorTest {

    private lateinit var app: DailyBeatApp

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    @Test
    fun seedingSameDateAndSeedTwiceDoesNotDuplicateRecords() = runBlocking(Dispatchers.IO) {
        val date = LocalDate.of(2026, 8, 30)

        val first = SyntheticDayGenerator.seedForDate(app, date, seed = 42)
        val second = SyntheticDayGenerator.seedForDate(app, date, seed = 42)
        val (start, end) = DayBounds.dayStartEnd(date)

        assertEquals(SyntheticDayGenerator.Result(7, 8), first)
        assertEquals(SyntheticDayGenerator.Result(0, 0), second)
        assertEquals(7, app.db.visits().between(start, end).size)
        assertEquals(8, app.db.events().eventsForDay(start, end).size)
    }
}
