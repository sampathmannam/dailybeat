package com.dailybeat.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isGeneratingWeekly: Boolean = false,
    val isExporting: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    val recentDiaries: StateFlow<List<DiaryEntry>> = app.diaryRepository.observeRecent(60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    fun generateWeeklyRollup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingWeekly = true, error = null, message = null)
            app.weeklyGenerator.generateAndSave().fold(
                onSuccess = { text ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingWeekly = false,
                        message = "Weekly rollup saved (${text.lines().firstOrNull()?.take(60) ?: "ok"}).",
                    )
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, error = null, message = null)
            try {
                val settings = app.settingsRepository.get()
                val file = app.packageExporter.exportWeekPackage(
                    officerName = settings.officerName,
                    supervisorName = settings.supervisorName,
                )
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    message = "Export saved: ${file.name}",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = e.message ?: "Export failed.",
                )
            }
        }
    }
}
