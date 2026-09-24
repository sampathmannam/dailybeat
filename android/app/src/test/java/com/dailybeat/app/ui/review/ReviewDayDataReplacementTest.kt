package com.dailybeat.app.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import java.time.LocalDate
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class ReviewDayDataReplacementTest {
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private lateinit var app: DailyBeatApp
    private val date = LocalDate.of(2026, 9, 24)
    private val dayStart get() = DayBounds.dayStartEnd(date).first

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

    private fun createViewModel() = ReviewDayViewModel(
        app, SavedStateHandle(mapOf("dateKey" to DateKeys.format(date))),
    ).also { model ->
        viewModelJobs += checkNotNull(model.viewModelScope.coroutineContext[Job])
        store.put("review-${viewModelJobs.size}", model)
    }

    private suspend fun insertVisit(): LocationVisit {
        val visit = LocationVisit(startMs = dayStart + 60_000L, endMs = dayStart + 11 * 60_000L,
            latitude = 11.40, longitude = 78.20, placeName = "Original stop")
        return visit.copy(id = app.visitRepository.insert(visit))
    }

    private fun startAction(model: ReviewDayViewModel, action: () -> Unit): List<Job> {
        val parent = checkNotNull(model.viewModelScope.coroutineContext[Job])
        val existing = parent.children.toSet()
        action()
        return parent.children.filter { it !in existing }.toList()
    }

    private suspend fun action(model: ReviewDayViewModel, action: () -> Unit) {
        val jobs = startAction(model, action)
        withTimeout(20_000L) {
            jobs.joinAll()
            model.uiState.first { !it.isSaving }
        }
    }

    @Test fun deletedVisitCannotLeaveAnOrphanLearnedPlace() = runBlocking {
        val oldVisit = insertVisit()
        val model = createViewModel()
        app.db.visits().deleteAll()

        action(model) { model.renameVisit(oldVisit, "Should not be learned") }

        assertTrue(app.placeRepository.all().isEmpty())
        assertTrue(app.db.visits().all().isEmpty())
        assertTrue(app.db.visits().allCorrections().isEmpty())
        assertNotNull(model.uiState.value.error)
    }

    @Test fun failedCorrectionRollsBackTheLearnedPlaceAndItsAudit() = runBlocking {
        val visit = insertVisit()
        val model = createViewModel()
        withContext(Dispatchers.IO) {
            app.db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_review BEFORE INSERT ON visit_corrections BEGIN SELECT RAISE(ABORT, 'simulated failure'); END",
            )
        }
        try {
            action(model) { model.renameVisit(visit, "Should roll back") }

            assertTrue(app.placeRepository.all().isEmpty())
            assertEquals(visit, app.db.visits().all().single())
            assertTrue(app.db.visits().allCorrections().isEmpty())
            assertNotNull(model.uiState.value.error)
        } finally {
            withContext(Dispatchers.IO) {
                app.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_review")
            }
        }
    }

    @Test fun changedVisitSnapshotIsRejectedEvenWithoutADataReplacement() = runBlocking {
        val oldVisit = insertVisit()
        val model = createViewModel()
        // Same reusable ID and label: checking only those fields would accept this stale row.
        val current = oldVisit.copy(longitude = oldVisit.longitude + 0.1)
        app.visitRepository.update(current)

        action(model) { model.renameVisit(oldVisit, "Stale location name") }
        action(model) { model.setVisitHidden(oldVisit, true) }

        assertEquals(listOf(current), app.db.visits().all())
        assertTrue(app.placeRepository.all().isEmpty())
        assertTrue(app.db.visits().allCorrections().isEmpty())
        assertNotNull(model.uiState.value.error)
    }

    @Test fun queuedTitleCannotReappearAfterEraseWhileWaitingForTheStorageGate() = runBlocking {
        val model = createViewModel()
        model.setTitle("Private unsaved title")
        val jobs = CaptureStorageGate.mutex.withLock {
            val pending = startAction(model, model::saveTitle)
            assertTrue(model.uiState.value.isSaving)
            assertTrue(pending.isNotEmpty())
            CaptureStorageGate.invalidatePersonalData()
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            pending
        }
        withTimeout(20_000L) { jobs.joinAll() }

        assertNull(app.beatRepository.get(date))
        assertEquals("", model.uiState.value.title)
        assertNotNull(model.uiState.value.error)
        assertFalse(model.uiState.value.isSaving)
    }

    @Test fun staleReviewCannotMutateRestoredRowsEvenWhenVisitIdIsReused() = runBlocking {
        val oldVisit = insertVisit()
        app.beatRepository.saveTitle(date, "Original title")
        val model = createViewModel()
        model.setTitle("Old draft title")
        val restored = oldVisit.copy(latitude = 12.30, longitude = 77.30, placeName = "Restored stop")
        CaptureStorageGate.mutex.withLock {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            app.visitRepository.insert(restored)
            app.beatRepository.complete(date, "Restored title")
            CaptureStorageGate.invalidatePersonalData()
        }
        val review = app.beatRepository.get(date)

        action(model) { model.renameVisit(oldVisit, "Stale correction") }
        action(model) { model.setVisitHidden(oldVisit, true) }
        model.setTitle("Another stale title")
        action(model, model::saveTitle)
        action(model, model::complete)
        action(model, model::reopen)

        assertEquals(listOf(restored), app.db.visits().all())
        assertTrue(app.placeRepository.all().isEmpty())
        assertTrue(app.db.visits().allCorrections().isEmpty())
        assertEquals(review, app.beatRepository.get(date))
        assertNotNull(model.uiState.value.error)
    }

    @Test fun freshReviewCanCorrectAndCompleteRestoredRecords() = runBlocking {
        val restored = insertVisit().copy(placeName = "Restored stop")
        CaptureStorageGate.mutex.withLock {
            app.visitRepository.update(restored)
            app.beatRepository.saveTitle(date, "Restored title")
            CaptureStorageGate.invalidatePersonalData()
        }
        val fresh = createViewModel()

        action(fresh) { fresh.renameVisit(restored, "Confirmed stop") }
        assertNull(fresh.uiState.value.error)
        val renamed = app.visitRepository.visitsForDate(date).single()
        action(fresh) { fresh.setVisitHidden(renamed, true) }
        assertNull(fresh.uiState.value.error)
        fresh.setTitle("Reviewed restored day")
        action(fresh, fresh::saveTitle)
        action(fresh, fresh::complete)
        assertEquals("complete", app.beatRepository.get(date)?.state)
        action(fresh, fresh::reopen)

        assertNull(fresh.uiState.value.error)
        assertEquals("Confirmed stop", app.db.visits().all().single().placeName)
        assertTrue(app.db.visits().all().single().hidden)
        assertEquals("Confirmed stop", app.placeRepository.all().single().name)
        assertEquals(2, app.db.visits().allCorrections().size)
        assertEquals("Reviewed restored day", app.beatRepository.get(date)?.title)
        assertEquals("needs_review", app.beatRepository.get(date)?.state)
        assertNull(app.beatRepository.get(date)?.completedAt)
    }

    @Test fun crossMidnightClippedCorrectionPreservesTheUnderlyingObservedTimes() = runBlocking {
        val raw = LocationVisit(startMs = dayStart - 10 * 60_000L, endMs = dayStart + 20 * 60_000L,
            latitude = 11.40, longitude = 78.20, placeName = "Overnight stop")
        val id = app.visitRepository.insert(raw)
        val clipped = app.visitRepository.visitsForDate(date).single()
        assertEquals(dayStart, clipped.startMs)
        val model = createViewModel()

        action(model) { model.renameVisit(clipped, "Confirmed overnight stop") }

        assertNull(model.uiState.value.error)
        val saved = app.db.visits().all().single()
        assertEquals(id, saved.id)
        assertEquals(raw.startMs, saved.startMs)
        assertEquals(raw.endMs, saved.endMs)
        assertEquals("Confirmed overnight stop", saved.placeName)
        assertEquals(id, app.db.visits().allCorrections().single().visitId)
    }

    @Test fun activeReviewCollectorsCannotRepopulateRetainedContentAfterEraseAndRestore() = runBlocking {
        val oldVisit = insertVisit()
        app.beatRepository.saveTitle(date, "Original private title")
        app.diaryRepository.saveForDate(date, "Original private diary")
        val model = createViewModel()
        val openedGeneration = CaptureStorageGate.dataGeneration.get()
        val lateStates = Collections.synchronizedList(mutableListOf<ReviewDayUiState>())
        val lateDays = Collections.synchronizedList(mutableListOf<DayFeedItem>())
        val lateVisits = Collections.synchronizedList(mutableListOf<List<LocationVisit>>())
        val collectors = mutableListOf<Job>()
        collectors += launch(Dispatchers.Main.immediate) {
            model.day.collect { if (CaptureStorageGate.dataGeneration.get() != openedGeneration) lateDays += it }
        }
        collectors += launch(Dispatchers.Main.immediate) {
            model.visits.collect { if (CaptureStorageGate.dataGeneration.get() != openedGeneration) lateVisits += it }
        }
        collectors += launch(Dispatchers.Main.immediate) {
            model.uiState.collect { if (CaptureStorageGate.dataGeneration.get() != openedGeneration) lateStates += it }
        }
        try {
            withTimeout(20_000L) {
                model.visits.first { it.singleOrNull()?.id == oldVisit.id }
                model.day.first { it.stays.isNotEmpty() && it.diaryPreview == "Original private diary" }
                model.uiState.first { it.diaryText == "Original private diary" }
            }
            model.setTitle("Retained unsaved private title")

            CaptureStorageGate.mutex.withLock {
                withContext(Dispatchers.IO) { app.db.clearAllTables() }
                CaptureStorageGate.invalidatePersonalData()
            }
            withTimeout(20_000L) {
                model.visits.first { it.isEmpty() }
                model.day.first { it.isEmpty }
                model.uiState.first { it.title.isEmpty() && it.diaryText == null && it.error != null }
            }

            // Keep the stale screen subscribed while restored Room rows emit again. A fresh
            // screen witnesses that the replacement is available; the old screen stays blank.
            CaptureStorageGate.mutex.withLock {
                app.visitRepository.insert(oldVisit.copy(placeName = "Restored private stop"))
                app.beatRepository.saveTitle(date, "Restored private title")
                app.diaryRepository.saveForDate(date, "Restored private diary")
                CaptureStorageGate.invalidatePersonalData()
            }
            val fresh = createViewModel()
            collectors += launch(Dispatchers.Main.immediate) { fresh.day.collect {} }
            collectors += launch(Dispatchers.Main.immediate) { fresh.visits.collect {} }
            withTimeout(20_000L) {
                fresh.day.first { it.stays.singleOrNull()?.name == "Restored private stop" && it.diaryPreview == "Restored private diary" }
                fresh.uiState.first { it.diaryText == "Restored private diary" }
            }

            assertTrue(model.visits.value.isEmpty())
            assertTrue(model.day.value.isEmpty)
            assertEquals("", model.uiState.value.title)
            assertNull(model.uiState.value.diaryText)
            assertEquals(0, model.uiState.value.eventCount)
            assertEquals(CaptureStorageGate.dataGeneration.get(), model.uiState.value.dataGeneration)
            assertNotNull(model.uiState.value.error)
            assertTrue(lateStates.isNotEmpty())
            assertTrue(synchronized(lateStates) { lateStates.all { it.title.isEmpty() && it.diaryText == null } })
            assertTrue(synchronized(lateDays) { lateDays.all { it.isEmpty && it.diaryPreview.isNullOrBlank() } })
            assertTrue(synchronized(lateVisits) { lateVisits.all { it.isEmpty() } })
        } finally {
            withTimeout(20_000L) { collectors.forEach { it.cancelAndJoin() } }
        }
    }
}
