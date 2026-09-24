package com.dailybeat.app.ui.feed

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
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
class FeedDataReplacementTest {
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var app: DailyBeatApp

    @Before fun before() = runBlocking {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
    }

    @After fun after() = runBlocking {
        store.clear()
        withTimeout(20_000L) { viewModelJobs.forEach { it.cancelAndJoin() } }
        withContext(Dispatchers.IO) { app.db.clearAllTables() }
        Dispatchers.resetMain()
    }

    private fun model() = FeedViewModel(app).also {
        viewModelJobs += checkNotNull(it.viewModelScope.coroutineContext[Job])
        store.put("feed-${viewModelJobs.size}", it)
    }

    private suspend fun visit(name: String, manuallyEdited: Boolean = false): LocationVisit {
        val start = DayBounds.dayStartEnd(DateKeys.today()).first + 60_000L
        val row = LocationVisit(startMs = start, endMs = start + 10 * 60_000L,
            latitude = 11.40, longitude = 78.20, placeName = name, manuallyEdited = manuallyEdited)
        return row.copy(id = app.visitRepository.insert(row))
    }

    private suspend fun replace(replacement: suspend () -> Unit = {}) {
        CaptureStorageGate.mutex.withLock {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            replacement()
            CaptureStorageGate.invalidatePersonalData()
        }
    }

    private suspend fun loaded(model: FeedViewModel, predicate: (FeedUiState) -> Boolean = { true }): FeedUiState =
        withTimeout(20_000L) { model.uiState.first { !it.isLoading && predicate(it) } }

    private suspend fun search(model: FeedViewModel, query: String): FeedUiState {
        model.search(query)
        dispatcher.scheduler.advanceTimeBy(300L)
        dispatcher.scheduler.runCurrent()
        return withTimeout(20_000L) { model.uiState.first { !it.isSearching } }
    }

    private suspend fun name(model: FeedViewModel, stay: DayStay, value: String, onSaved: () -> Unit = {}) {
        val parent = checkNotNull(model.viewModelScope.coroutineContext[Job])
        val existing = parent.children.toSet()
        model.saveNamedPlace(stay, value, onSaved)
        val jobs = parent.children.filter { it !in existing }.toList()
        withTimeout(20_000L) {
            jobs.joinAll()
            model.uiState.first { !it.isSavingPlace }
        }
    }

    @Test fun eraseClearsCachedFeedAndCompletedSearchResults() = runBlocking {
        visit("Old private stop")
        app.eventRepository.addManualEvent("Old private note")
        val model = model()
        loaded(model) { it.days.any { day -> day.stays.isNotEmpty() } }
        assertTrue(search(model, "Old private").searchResults.isNotEmpty())

        replace()
        val cleared = loaded(model) { it.days.isEmpty() }

        assertEquals(CaptureStorageGate.dataGeneration.get(), cleared.dataGeneration)
        assertEquals("", cleared.searchQuery)
        assertTrue(cleared.searchResults.isEmpty())
        assertFalse(cleared.isSearching)
        assertNull(cleared.exportPath)
    }

    @Test fun restoreReloadsTheFeedAndEveryLoadedStayCarriesTheCurrentGeneration() = runBlocking {
        val original = visit("Old stop")
        val model = model()
        val before = loaded(model) { it.days.flatMap { day -> day.stays }.isNotEmpty() }
        val oldStay = before.days.single().stays.single()
        assertEquals(CaptureStorageGate.dataGeneration.get(), oldStay.dataGeneration)
        search(model, "Old stop")

        replace { app.visitRepository.insert(original.copy(placeName = "Restored stop")) }
        val generation = CaptureStorageGate.dataGeneration.get()
        val after = loaded(model) { state ->
            state.days.flatMap { it.stays }.any { it.name == "Restored stop" && it.dataGeneration == generation }
        }

        assertEquals(listOf("Restored stop"), after.days.flatMap { it.stays }.map { it.name })
        assertEquals(generation, after.dataGeneration)
        assertTrue(after.days.flatMap { it.stays }.all { it.dataGeneration == generation })
        assertNotEquals(oldStay.dataGeneration, generation)
        assertEquals("", after.searchQuery)
        assertTrue(after.searchResults.isEmpty())
        val results = search(model, "Restored stop").searchResults
        assertEquals(listOf("Restored stop"), results.map { it.snippet })
    }

    @Test fun retainedNamingSnapshotCannotModifyAnIdenticalRestoredVisit() = runBlocking {
        val original = visit("Confirmed stop", manuallyEdited = true)
        val model = model()
        val retained = loaded(model) { it.days.isNotEmpty() }.days.single().stays.single()
        replace { app.visitRepository.insert(original) } // Same ID, label, coordinates, and times.
        val generation = CaptureStorageGate.dataGeneration.get()
        loaded(model) { it.days.flatMap { day -> day.stays }.any { it.dataGeneration == generation } }
        var callbacks = 0

        name(model, retained, "Stale rename") { callbacks++ }

        assertEquals(listOf(original), app.db.visits().all())
        assertTrue(app.db.visits().allCorrections().isEmpty())
        assertTrue(app.placeRepository.all().isEmpty())
        assertEquals(0, callbacks)
        assertNotNull(model.uiState.value.error)
    }

    @Test fun freshLoadedStayCanBeNamedAfterRestoreInTheSameFeedViewModel() = runBlocking {
        val original = visit("Unnamed place")
        val model = model()
        loaded(model) { it.days.isNotEmpty() }
        replace { app.visitRepository.insert(original) }
        val generation = CaptureStorageGate.dataGeneration.get()
        val fresh = loaded(model) { it.days.flatMap { day -> day.stays }.any { it.dataGeneration == generation } }
            .days.single().stays.single()

        name(model, fresh, "Fresh saved name")

        assertNull(model.uiState.value.error)
        assertEquals("Fresh saved name", app.placeRepository.all().single().name)
        assertEquals(listOf(original), app.db.visits().all())
        loaded(model) { it.days.flatMap { day -> day.stays }.any { it.name == "Fresh saved name" } }
        Unit
    }

    @Test fun eraseCancelsAStillDebouncingSearchWithoutRecreatingItsQueryOrHits() = runBlocking {
        visit("Private stop")
        val model = model()
        loaded(model) { it.days.isNotEmpty() }
        model.search("Private")
        assertTrue(model.uiState.value.isSearching)

        replace()
        dispatcher.scheduler.advanceTimeBy(300L)
        dispatcher.scheduler.runCurrent()
        val cleared = loaded(model) { it.days.isEmpty() }

        assertEquals("", cleared.searchQuery)
        assertTrue(cleared.searchResults.isEmpty())
        assertFalse(cleared.isSearching)
    }
}
