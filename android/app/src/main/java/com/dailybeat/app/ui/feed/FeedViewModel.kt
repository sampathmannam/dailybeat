package com.dailybeat.app.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class FeedUiState(
    val days: List<DayFeedItem> = emptyList(),
    val isLoading: Boolean = true,
    val isGeneratingWeekly: Boolean = false,
    val isExporting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val days = withContext(Dispatchers.IO) { loadDays() }
            _uiState.value = _uiState.value.copy(days = days, isLoading = false)
        }
    }

    private suspend fun loadDays(): List<DayFeedItem> {
        val today = DateKeys.today()
        return (0 until DAYS_IN_FEED)
            .map { today.minusDays(it.toLong()) }
            .map { date -> buildDay(date) }
            .filterNot { it.isEmpty }
    }

    private suspend fun buildDay(date: LocalDate): DayFeedItem {
        val (start, end) = DayBounds.dayStartEnd(date)
        return DayFeedBuilder.build(
            date = date,
            visits = app.visitRepository.visitsForDate(date).filter { it.startMs in start..end },
            diaryText = app.diaryRepository.textForDate(date),
        )
    }

    fun generateWeeklyRollup() {
        if (_uiState.value.isGeneratingWeekly) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingWeekly = true, error = null, message = null)
            app.weeklyGenerator.generateAndSave().fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWeekly = false,
                        message = "Weekly rollup saved to today's diary.",
                    )
                    refresh()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWeekly = false,
                        error = error.message ?: "Weekly report failed.",
                    )
                },
            )
        }
    }

    fun exportPackage() {
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null, message = null)
            val settings = app.settingsRepository.get()
            runCatching {
                // Zipping a month of diaries and rendering their PDFs is heavy disk work.
                withContext(Dispatchers.IO) {
                    app.packageExporter.exportWeekPackage(
                        officerName = settings.officerName,
                        supervisorName = settings.supervisorName,
                    )
                }
            }.fold(
                onSuccess = { file ->
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        message = "Export saved: ${file.name}",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = error.message ?: "Export failed.",
                    )
                },
            )
        }
    }

    private companion object {
        const val DAYS_IN_FEED = 30
    }
}
