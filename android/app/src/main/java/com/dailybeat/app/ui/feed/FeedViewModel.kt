package com.dailybeat.app.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.domain.VisitLabels
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
import com.dailybeat.app.util.userMessage
import com.dailybeat.app.util.InputPolicy

data class FeedUiState(
    val dataGeneration: Long = CaptureStorageGate.dataGeneration.get(),
    val throughDate: LocalDate = DateKeys.today(),
    val days: List<DayFeedItem> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<com.dailybeat.app.data.db.JournalSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val isLoading: Boolean = true,
    val isGeneratingWeekly: Boolean = false,
    val isExporting: Boolean = false,
    val isSavingPlace: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val exportPath: String? = null,
)

class FeedViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private val openedDataGeneration = CaptureStorageGate.dataGeneration.get()
    private var observedDataGeneration = openedDataGeneration

    fun search(value: String) {
        val query = InputPolicy.bounded(value, 120)
        searchJob?.cancel()
        _uiState.value = _uiState.value.copy(searchQuery = query, searchResults = emptyList(),
            isSearching = query.isNotBlank(), error = null)
        if (query.isBlank()) return
        val generation = CaptureStorageGate.dataGeneration.get()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)
            try {
                val results = app.db.journalSearch().search(com.dailybeat.app.data.db.JournalSearchDao.pattern(query))
                val places = app.placeRepository.all()
                if (generation != CaptureStorageGate.dataGeneration.get() || _uiState.value.searchQuery != query) return@launch
                _uiState.value = _uiState.value.copy(
                    searchResults = results.map { it.copy(snippet = it.displaySnippet(places)) }, isSearching = false,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(isSearching = false, error = "Unable to search local history. Try again.")
            }
        }
    }

    init {
        refresh()
        viewModelScope.launch {
            CaptureStorageGate.dataChanges.collect {
                val generation = CaptureStorageGate.dataGeneration.get()
                if (generation != observedDataGeneration) {
                    observedDataGeneration = generation
                    refreshJob?.cancel()
                    searchJob?.cancel()
                    _uiState.value = FeedUiState(throughDate = _uiState.value.throughDate)
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        // Initial load, navigation and foreground resume may overlap. Only the newest load
        // may publish state; cancellation must not surface as a user-visible load error.
        refreshJob?.cancel()
        val generation = CaptureStorageGate.dataGeneration.get()
        val throughDate = _uiState.value.throughDate
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { DayFeedLoader(app.db).load(throughDate) } }.fold(
                onSuccess = { days ->
                    if (generation == CaptureStorageGate.dataGeneration.get()) {
                        val currentDays = days.map { day ->
                            day.copy(stays = day.stays.map { it.copy(dataGeneration = generation) })
                        }
                        _uiState.value = _uiState.value.copy(days = currentDays, isLoading = false)
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.userMessage("Unable to load captured days."),
                    )
                },
            )
        }
    }

    fun browseThrough(date: LocalDate) {
        _uiState.value = _uiState.value.copy(throughDate = minOf(date, DateKeys.today()))
        refresh()
    }
    fun olderDays() = browseThrough(_uiState.value.throughDate.minusDays(30))
    fun newerDays() = browseThrough(_uiState.value.throughDate.plusDays(30))

    fun generateWeeklyRollup() {
        if (_uiState.value.isGeneratingWeekly || _uiState.value.isExporting) return
        val generation = CaptureStorageGate.dataGeneration.get()
        _uiState.value = _uiState.value.copy(isGeneratingWeekly = true, error = null, message = null)
        viewModelScope.launch {
            val result = runCatching { app.weeklyGenerator.generateAndSave() }
                .getOrElse { Result.failure(it) }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            if (generation != CaptureStorageGate.dataGeneration.get()) return@launch
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
                        error = error.userMessage("Weekly report failed."),
                    )
                },
            )
        }
    }

    suspend fun preparePackage(): List<com.dailybeat.app.export.DiarySharePreview>? {
        val generation = CaptureStorageGate.dataGeneration.get()
        return try {
            val previews = app.diaryShareService.prepareWeek()
            if (generation != CaptureStorageGate.dataGeneration.get()) return null
            check(previews.isNotEmpty()) { "No saved diaries in the last seven days." }
            previews
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (generation == CaptureStorageGate.dataGeneration.get()) {
                _uiState.value = _uiState.value.copy(error = error.userMessage("Unable to prepare sharing copy."))
            }
            null
        }
    }

    fun exportPackage(previews: List<com.dailybeat.app.export.DiarySharePreview>) {
        if (_uiState.value.isExporting || _uiState.value.isGeneratingWeekly) return
        val generation = CaptureStorageGate.dataGeneration.get()
        _uiState.value = _uiState.value.copy(isExporting = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    app.packageExporter.exportWeekPackage(previews)
                }
            }.fold(
                onSuccess = { file ->
                    if (generation != CaptureStorageGate.dataGeneration.get()) return@fold
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        message = "Export ready: ${file.name}",
                        exportPath = file.absolutePath,
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (generation != CaptureStorageGate.dataGeneration.get()) return@fold
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        error = error.userMessage("Export failed."),
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
    fun saveNamedPlace(stay: DayStay, name: String, onSaved: () -> Unit = {}) {
        if (_uiState.value.isSavingPlace) return
        val trimmed = InputPolicy.singleLine(name, InputPolicy.PLACE_NAME_CHARS).trim()
        if (trimmed.isEmpty()) return
        if (!stay.canBeNamed) {
            _uiState.value = _uiState.value.copy(
                message = null,
                error = "This stop has no reliable location to name.",
            )
            return
        }
        val expectedDataGeneration = stay.dataGeneration ?: openedDataGeneration
        if (expectedDataGeneration != CaptureStorageGate.dataGeneration.get()) {
            _uiState.value = _uiState.value.copy(error = "Local data changed. Reopen the day before naming this stop.")
            return
        }
        _uiState.value = _uiState.value.copy(isSavingPlace = true, error = null, message = null)
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(expectedDataGeneration) {
                    app.db.withTransaction {
                        val visit = if (stay.visitId > 0) {
                            app.db.visits().between(stay.startMs, stay.endMs).singleOrNull { it.id == stay.visitId }
                                .also { current ->
                                    check(current != null && !current.hidden &&
                                        current.latitude == stay.latitude && current.longitude == stay.longitude &&
                                        VisitLabels.displayName(current, app.placeRepository.all(), shortAddress = true) == stay.name
                                    ) { "This stop changed. Reopen the day and try again." }
                                }
                        } else null
                        if (visit?.manuallyEdited == true && VisitLabels.usable(visit.placeName) != null) {
                            // A deliberate edit to an already corrected stop changes that stop,
                            // not the broader saved place surrounding it. Keep its audit trail.
                            app.visitRepository.rename(visit, trimmed)
                        } else if (!app.placeRepository.renameExactMatch(
                                stay.name, stay.latitude, stay.longitude, trimmed,
                            )) {
                            app.placeRepository.add(trimmed, stay.latitude, stay.longitude, radiusM = PLACE_RADIUS_M)
                        }
                    }
                }
            }.fold(
                onSuccess = {
                    if (expectedDataGeneration != CaptureStorageGate.dataGeneration.get()) return@fold
                    _uiState.value = _uiState.value.copy(
                        isSavingPlace = false,
                        message = "Saved \"$trimmed\".",
                        error = null,
                    )
                    onSaved()
                    refresh()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (expectedDataGeneration != CaptureStorageGate.dataGeneration.get()) return@fold
                    _uiState.value = _uiState.value.copy(
                        isSavingPlace = false,
                        error = error.userMessage("Unable to save this place."),
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
        const val PLACE_RADIUS_M = 150
    }
}
