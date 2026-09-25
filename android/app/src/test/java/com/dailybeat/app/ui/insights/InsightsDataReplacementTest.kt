package com.dailybeat.app.ui.insights

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class InsightsDataReplacementTest {
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private lateinit var app: DailyBeatApp

    @Before fun before() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    @After fun after() = runBlocking {
        store.clear()
        withTimeout(20_000L) { viewModelJobs.forEach { it.cancelAndJoin() } }
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
        Dispatchers.resetMain()
    }

    private fun model() = InsightsViewModel(app).also {
        viewModelJobs += checkNotNull(it.viewModelScope.coroutineContext[Job])
        store.put("insights-${viewModelJobs.size}", it)
    }

    private suspend fun visit(name: String?, daysAgo: Long = 0, address: String? = null) {
        val date = DateKeys.today().minusDays(daysAgo)
        val start = DayBounds.dayStartEnd(date).first + 60_000L
        app.visitRepository.insert(LocationVisit(startMs = start, endMs = start + 10 * 60_000L,
            latitude = 11.40, longitude = 78.20, placeName = name, address = address))
    }

    private suspend fun replace(replacement: suspend () -> Unit = {}) {
        CaptureStorageGate.mutex.withLock {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            replacement()
            CaptureStorageGate.invalidatePersonalData()
        }
    }

    private suspend fun loaded(model: InsightsViewModel, predicate: (InsightsUiState) -> Boolean = { true }) =
        withTimeout(20_000L) { model.uiState.first { !it.isLoading && predicate(it) } }

    @Test fun eraseClearsCachedPatternsReviewCountsAndRoutes() = runBlocking {
        visit("Private repeated stop")
        visit("Private repeated stop", daysAgo = 1)
        app.beatRepository.complete(DateKeys.today(), "Private reviewed day")
        val model = model()
        val before = loaded(model) { it.days.size == 2 }
        assertEquals(listOf(PlacePattern("Private repeated stop", 2)), before.recurringPlaces)
        assertEquals(1, before.reviewedDays)
        assertEquals(1, before.reviewStreak)

        replace()
        val cleared = loaded(model) { it.days.isEmpty() }

        assertTrue(cleared.recurringPlaces.isEmpty())
        assertEquals(0, cleared.reviewedDays)
        assertEquals(0, cleared.reviewStreak)
        assertEquals(0L, cleared.weeklyTrackedMinutes)
        assertEquals(0.0, cleared.weeklyDistanceKm, 0.0)
        assertTrue(cleared.weekDays.all { it.isEmpty })
        assertEquals("Build your first Beat", cleared.insightTitle)
        assertNull(cleared.insightDate)
    }

    @Test fun restoreAutomaticallyReplacesInsightsWithOnlyTheNewHistory() = runBlocking {
        visit("Old private place")
        visit("Old private place", daysAgo = 1)
        val model = model()
        loaded(model) { it.recurringPlaces.any { pattern -> pattern.name == "Old private place" } }

        replace {
            visit("Restored place", daysAgo = 2)
            visit("Restored place", daysAgo = 3)
        }
        val restored = loaded(model) { it.recurringPlaces.any { pattern -> pattern.name == "Restored place" } }

        assertEquals(listOf(PlacePattern("Restored place", 2)), restored.recurringPlaces)
        assertEquals(setOf(DateKeys.today().minusDays(2), DateKeys.today().minusDays(3)), restored.days.map { it.date }.toSet())
        assertTrue(restored.days.flatMap { it.stays }.none { it.name == "Old private place" })
        assertEquals(DateKeys.today().minusDays(2), restored.insightDate)
        assertNull(restored.error)
    }

    @Test fun unknownDestinationsNeverBecomeAFabricatedRepeatedPlacePattern() = runBlocking {
        visit("Unnamed place")
        visit(null, daysAgo = 1, address = "Unnamed place")
        visit("Unknown location", daysAgo = 2)
        visit("Known library", daysAgo = 3)
        visit("Known library", daysAgo = 4)
        val model = model()

        val state = loaded(model) { it.days.size == 5 }

        assertEquals(3, state.days.flatMap { it.stays }.count { VisitLabels.isApproximate(it.name) })
        assertEquals(listOf(PlacePattern("Known library", 2)), state.recurringPlaces)
        assertTrue(state.recurringPlaces.none { VisitLabels.isFallback(it.name) })
    }
}
