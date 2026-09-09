package com.dailybeat.app.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val exportPath: String? = null,
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        // Initial load, navigation and foreground resume may overlap. Only the newest load
        // may publish state; cancellation must not surface as a user-visible load error.
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { loadDays() } }.fold(
                onSuccess = { days ->
                    _uiState.value = _uiState.value.copy(days = days, isLoading = false)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Unable to load captured days.",
                    )
                },
            )
        }
    }

    private suspend fun loadDays(): List<DayFeedItem> {
        val today = DateKeys.today()
        val places = app.placeRepository.all()
        return (0 until DAYS_IN_FEED)
            .map { today.minusDays(it.toLong()) }
            .map { date -> buildDay(date, places) }
            .filterNot { it.isEmpty }
    }

    private suspend fun buildDay(date: LocalDate, places: List<Place>): DayFeedItem {
        return DayFeedBuilder.build(
            date = date,
            visits = app.visitRepository.visitsForDate(date),
            diaryText = app.diaryRepository.textForDate(date),
            places = places,
        )
    }

    fun generateWeeklyRollup() {
        if (_uiState.value.isGeneratingWeekly) return
        _uiState.value = _uiState.value.copy(isGeneratingWeekly = true, error = null, message = null)
        viewModelScope.launch {
            val result = runCatching { app.weeklyGenerator.generateAndSave() }
                .getOrElse { Result.failure(it) }
            result.fold(
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
        _uiState.value = _uiState.value.copy(isExporting = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                val settings = app.settingsRepository.get()
                // Zipping a week of diaries and rendering their PDFs is heavy disk work.
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
                        message = "Export ready: ${file.name}",
                        exportPath = file.absolutePath,
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

    /**
     * Saves a stay's own coordinates as a named place. OpenStreetMap has no POI at many real
     * stops, so reverse geocoding can only offer the road; naming it once teaches the app, and
     * [com.dailybeat.app.domain.GeofenceMatcher] labels every later stay there.
     */
    fun saveNamedPlace(stay: DayStay, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (!stay.canBeNamed) {
            _uiState.value = _uiState.value.copy(
                message = null,
                error = "This stop has no reliable location to name.",
            )
            return
        }
        viewModelScope.launch {
            runCatching {
                app.placeRepository.add(trimmed, stay.latitude, stay.longitude, radiusM = PLACE_RADIUS_M)
            }.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        message = "Saved \"$trimmed\". Future stays here will use it.",
                        error = null,
                    )
                    refresh()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = error.message ?: "Unable to save this place.",
                    )
                },
            )
        }
    }

    fun consumeExport() {
        _uiState.value = _uiState.value.copy(exportPath = null)
    }

    fun onExportShareFailed() {
        _uiState.value = _uiState.value.copy(
            error = "The export was created, but no app could open the share sheet.",
        )
    }

    private companion object {
        const val DAYS_IN_FEED = 30
        const val PLACE_RADIUS_M = 150
    }
}
