package com.dailybeat.app.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
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
class DiaryDataReplacementTest {
    private val store = ViewModelStore()
    private val viewModelJobs = mutableListOf<Job>()
    private lateinit var app: DailyBeatApp

    @Before fun before() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
    }

    @After fun after() = runBlocking {
        // clear() requests cancellation, but Room/IO continuations can still be unwinding.
        // Keep Main installed until every owned ViewModel coroutine has actually finished.
        store.clear()
        withTimeout(20_000L) {
            viewModelJobs.forEach { it.cancelAndJoin() }
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel(saved: SavedStateHandle) = DiaryViewModel(app, saved).also { model ->
        viewModelJobs += checkNotNull(model.viewModelScope.coroutineContext[Job])
        store.put("diary", model)
    }

    @Test fun retainedDraftCannotBeFlushedAfterEraseAndFreshEditingStillWorks() = runBlocking {
        val saved = SavedStateHandle(mapOf("diary_draft" to "Private unsaved draft"))
        val viewModel = createViewModel(saved)
        CaptureStorageGate.mutex.withLock {
            CaptureStorageGate.invalidatePersonalData()
            withContext(Dispatchers.IO) { app.db.clearAllTables() }
        }

        viewModel.prepareShare()

        assertNull(saved.get<String>("diary_draft"))
        assertEquals("", viewModel.uiState.value.text)
        assertNull(app.diaryRepository.todayText())

        viewModel.updateDiaryText("Fresh diary after erase")
        viewModel.prepareShare()
        assertEquals("Fresh diary after erase", app.diaryRepository.todayText())
    }

    @Test fun successfulRestoreReplacesRetainedDraftWithRestoredDiary() = runBlocking {
        withTimeout(20_000) {
            val saved = SavedStateHandle(mapOf("diary_draft" to "Old unsaved draft"))
            val viewModel = createViewModel(saved)
            CaptureStorageGate.mutex.withLock {
                app.diaryRepository.saveToday("Restored diary")
                CaptureStorageGate.invalidatePersonalData()
            }
            viewModel.uiState.first { it.text == "Restored diary" }
            assertNull(saved.get<String>("diary_draft"))
            assertEquals("Restored diary", viewModel.uiState.value.text)
        }
    }
}
