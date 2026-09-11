package com.dailybeat.app.ui.review

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
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
    val date: LocalDate = DateKeys.parseOrToday(savedStateHandle["dateKey"])

    val visits = app.visitRepository.observeForDate(date)
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

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value.take(120)) }
    }

    fun saveTitle() = launchAction("Title saved.", "Unable to save the title.") {
        app.beatRepository.saveTitle(date, _uiState.value.title)
    }

    fun renameVisit(visit: LocationVisit, name: String) {
        val cleanName = name.trim().take(120)
        if (cleanName.isEmpty()) return
        launchAction("Stop updated.", "Unable to update this stop.") {
            app.visitRepository.update(
                visit.copy(
                    placeName = cleanName,
                    manuallyEdited = true,
                    reviewState = "confirmed",
                ),
            )
        }
    }

    fun setVisitHidden(visit: LocationVisit, hidden: Boolean) {
        launchAction(
            if (hidden) "Stop hidden from this Beat." else "Stop restored.",
            "Unable to update this stop.",
        ) {
            app.visitRepository.update(
                visit.copy(hidden = hidden, manuallyEdited = true, reviewState = "confirmed"),
            )
        }
    }

    fun complete() = launchAction("Beat completed.", "Unable to complete this Beat.") {
        app.beatRepository.complete(date, _uiState.value.title.ifBlank { day.value.title })
    }

    fun reopen() = launchAction("Beat reopened for review.", "Unable to reopen this Beat.") {
        app.beatRepository.reopen(date)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, error = null) }

    private fun launchAction(success: String, failure: String, block: suspend () -> Unit) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null, error = null) }
        viewModelScope.launch {
            runCatching { block() }.fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, message = success) } },
                onFailure = { cause ->
                    if (cause is CancellationException) throw cause
                    _uiState.update { it.copy(isSaving = false, error = cause.userMessage(failure)) }
                },
            )
        }
    }
}
