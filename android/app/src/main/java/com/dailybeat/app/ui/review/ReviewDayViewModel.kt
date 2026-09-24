package com.dailybeat.app.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.InputPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.dailybeat.app.util.userMessage

data class ReviewDayUiState(
    val dataGeneration: Long = CaptureStorageGate.dataGeneration.get(),
    val title: String = "",
    val diaryText: String? = null,
    val eventCount: Int = 0,
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

private data class ReviewDayContent(
    val day: DayFeedItem,
    val diaryText: String?,
)

class ReviewDayViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    private val openedDataGeneration = CaptureStorageGate.dataGeneration.get()
    val date: LocalDate = DateKeys.parseOrToday(savedStateHandle["dateKey"])

    val visits = app.visitRepository.observeForDate(date)
        .combine(CaptureStorageGate.dataChanges) { rows, _ ->
            if (openedDataGeneration == CaptureStorageGate.dataGeneration.get()) rows else emptyList()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val content = combine(
        visits,
        app.breadcrumbRepository.observeForDate(date),
        app.beatRepository.observe(date),
        app.placeRepository.observeAll(),
        app.diaryRepository.observeForDate(date),
    ) { visits, breadcrumbs, review, places, diary ->
        ReviewDayContent(
            day = DayFeedBuilder.build(date, visits, diary?.text, places, breadcrumbs, review),
            diaryText = diary?.text,
        )
    }

    val day = content
        .combine(app.eventRepository.observeEventsForDate(date)) { content, events ->
            if (openedDataGeneration != CaptureStorageGate.dataGeneration.get()) {
                _uiState.value = ReviewDayUiState(error = DATA_CHANGED_MESSAGE)
                return@combine DayFeedBuilder.build(date, emptyList(), null)
            }
            _uiState.update { current ->
                current.copy(
                    title = if (current.title.isBlank()) content.day.title else current.title,
                    diaryText = content.diaryText,
                    eventCount = events.size,
                )
            }
            content.day
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DayFeedBuilder.build(date, emptyList(), null),
        )

    private val _uiState = MutableStateFlow(ReviewDayUiState())
    val uiState: StateFlow<ReviewDayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            CaptureStorageGate.dataChanges.collect {
                if (openedDataGeneration != CaptureStorageGate.dataGeneration.get()) {
                    _uiState.value = ReviewDayUiState(error = DATA_CHANGED_MESSAGE)
                }
            }
        }
    }

    fun setTitle(value: String) {
        if (rejectReplacedData()) return
        _uiState.update {
            it.copy(title = InputPolicy.singleLine(value, InputPolicy.BEAT_TITLE_CHARS))
        }
    }

    fun saveTitle() {
        val title = _uiState.value.title
        launchAction("Title saved.", "Unable to save the title.") {
            app.beatRepository.saveTitle(date, title)
        }
    }

    fun renameVisit(visit: LocationVisit, name: String) {
        val cleanName = InputPolicy.singleLine(name, InputPolicy.PLACE_NAME_CHARS).trim()
        if (cleanName.isEmpty()) return
        launchAction(
            "Stop updated.",
            "Unable to update this stop.",
        ) {
            requireCurrentVisit(visit)
            val existingPlace = GeofenceMatcher.matchPlace(
                visit.latitude,
                visit.longitude,
                app.placeRepository.all(),
            )
            if (existingPlace == null) {
                app.placeRepository.add(
                    cleanName,
                    visit.latitude,
                    visit.longitude,
                    radiusM = LEARNED_PLACE_RADIUS_M,
                )
            }
            app.visitRepository.rename(visit, cleanName)
        }
    }

    fun setVisitHidden(visit: LocationVisit, hidden: Boolean) {
        launchAction(
            if (hidden) "Stop hidden from this Beat." else "Stop restored.",
            "Unable to update this stop.",
        ) {
            requireCurrentVisit(visit)
            app.visitRepository.setHidden(visit, hidden)
        }
    }

    fun complete() {
        val title = _uiState.value.title.ifBlank { day.value.title }
        launchAction("Beat completed.", "Unable to complete this Beat.") {
            app.beatRepository.complete(date, title)
        }
    }

    fun reopen() = launchAction("Beat reopened for review.", "Unable to reopen this Beat.") {
        app.beatRepository.reopen(date)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, error = null) }

    private fun launchAction(success: String, failure: String, block: suspend () -> Unit) {
        if (rejectReplacedData()) return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(openedDataGeneration) {
                    // A learned place and the visit correction either both commit or neither
                    // does. Failed/stale review must not leave an orphan saved location.
                    app.db.withTransaction { block() }
                }
            }.fold(
                onSuccess = {
                    if (!rejectReplacedData()) {
                        _uiState.update { it.copy(isSaving = false, message = success) }
                    }
                },
                onFailure = { cause ->
                    if (cause is CancellationException) throw cause
                    _uiState.update { it.copy(isSaving = false, error = cause.userMessage(failure)) }
                },
            )
        }
    }

    private fun rejectReplacedData(): Boolean {
        if (openedDataGeneration == CaptureStorageGate.dataGeneration.get()) return false
        _uiState.value = ReviewDayUiState(error = DATA_CHANGED_MESSAGE)
        return true
    }

    private suspend fun requireCurrentVisit(visit: LocationVisit) {
        // Compare the same day-clipped snapshot shown by this screen, not just a reusable ID.
        check(app.visitRepository.visitsForDate(date).singleOrNull { it.id == visit.id } == visit) {
            "This stop changed. Reopen the day and try again."
        }
    }

    private companion object {
        const val LEARNED_PLACE_RADIUS_M = 150
        const val DATA_CHANGED_MESSAGE = "Local data changed. Reopen this day before making changes."
    }
}
