package com.dailybeat.app.ui.today

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.LocationVisit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class PatternHistoryRefreshTest {
    private val store = ViewModelStore()
    @Before fun before() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }

    @Test fun hidingAnOlderVisitImmediatelyRemovesItsPatternWithoutChangingToday() = runBlocking<Unit> {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val today = LocalDate.now()
        fun visit(daysAgo: Long): LocationVisit {
            val start = today.minusDays(daysAgo).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return LocationVisit(startMs = start, endMs = start + 60_000,
                latitude = 11.0, longitude = 78.0, placeName = "Library")
        }
        val older = visit(2)
        val id = app.db.visits().insert(older)
        app.db.visits().insert(visit(1))
        val model = TodayViewModel(app).also { store.put("today", it) }
        val observation = launch(Dispatchers.Default) { model.patternAnalysis.collect() }
        try {
            withTimeout(10_000) { model.patternAnalysis.first { it.recurringPlace == "Library" } }
            app.db.visits().update(older.copy(id = id, hidden = true))
            withTimeout(5_000) {
                model.patternAnalysis.first { it.observedDays == 1 && it.recurringPlace == null && it.suggestions.isEmpty() }
            }
        } finally { observation.cancelAndJoin() }
    }
}
