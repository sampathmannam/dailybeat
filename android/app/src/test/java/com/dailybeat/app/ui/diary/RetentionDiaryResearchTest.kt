package com.dailybeat.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.retention.HistoryRetentionManager
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Retention may remove old history, never an unrelated unsaved current-day draft. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class RetentionDiaryResearchTest {
    private val store = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var app: DailyBeatApp
    private val currentDate = LocalDate.parse("2026-09-17")
    private val now = Instant.parse("2026-09-17T12:00:00Z").toEpochMilli()

    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }
    @After fun after() = runBlocking {
        store.clear()
        withTimeout(20_000L) { jobs.forEach { it.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private fun model(saved: SavedStateHandle) = DiaryViewModel(app, saved).also {
        jobs += checkNotNull(it.viewModelScope.coroutineContext[Job])
        store.put("diary", it)
    }
    private suspend fun pruneAnOldDiary() {
        app.db.diaries().upsert(DiaryEntry("2026-08-18", "Synthetic old history", now))
        HistoryRetentionManager(app.db, clock = { now }, zoneId = ZoneOffset.UTC).prune(30)
    }

    @Test fun currentDraftSurvivesDeletingAnotherDayAndCanStillBeSaved() = runBlocking {
        val saved = SavedStateHandle(mapOf("dateKey" to currentDate.toString(), "diary_draft" to "Keep this current draft"))
        val viewModel = model(saved)
        pruneAnOldDiary()
        viewModel.prepareShare() // Flushes a retained manual edit through the real save path.
        assertEquals("Keep this current draft", saved.get<String>("diary_draft"))
        assertEquals("Keep this current draft", app.diaryRepository.textForDate(currentDate))
    }

    @Test fun delayedAutosaveOfCurrentDaySurvivesRetentionOfOlderHistory() = runBlocking {
        val viewModel = model(SavedStateHandle(mapOf("dateKey" to currentDate.toString())))
        viewModel.updateDiaryText("Current draft waiting for autosave")
        pruneAnOldDiary()
        dispatcher.scheduler.advanceTimeBy(501L)
        dispatcher.scheduler.runCurrent()
        withTimeout(20_000L) {
            app.diaryRepository.observeForDate(currentDate).first { it?.text == "Current draft waiting for autosave" }
        }
        assertEquals("Current draft waiting for autosave", viewModel.uiState.value.text)
    }

    @Test fun expiredDraftCannotBeFlushedAfterRetentionDeletesItsDay() = runBlocking {
        val saved = SavedStateHandle(mapOf("dateKey" to "2026-08-18", "diary_draft" to "Expired unsaved draft"))
        val viewModel = model(saved)
        pruneAnOldDiary()
        viewModel.prepareShare()
        assertNull(saved.get<String>("diary_draft"))
        assertNull(app.diaryRepository.textForDate(LocalDate.parse("2026-08-18")))
    }

    @Test fun preservingACurrentDraftAfterRetentionNeverBypassesALaterErase() = runBlocking {
        val saved = SavedStateHandle(mapOf("dateKey" to currentDate.toString(), "diary_draft" to "Current private draft"))
        val viewModel = model(saved)
        pruneAnOldDiary()
        CaptureStorageGate.mutex.withLock {
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
            CaptureStorageGate.invalidatePersonalData()
        }
        viewModel.prepareShare()
        assertNull(saved.get<String>("diary_draft"))
        assertNull(app.diaryRepository.textForDate(currentDate))
    }
}
