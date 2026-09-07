package com.dailybeat.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.capture.VoiceCaptureOrchestrator
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val visitCount: Int = 0,
    val eventCount: Int = 0,
    val hasDiary: Boolean = false,
    val cloudBrainReady: Boolean = false,
    val gpsEnabled: Boolean = true,
    val gpsActive: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val isSeeding: Boolean = false,
    val seedMessage: String? = null,
    val isRecordingVoice: Boolean = false,
    val voiceMessage: String? = null,
    val error: String? = null,
)

private data class TodayDataState(
    val visitCount: Int,
    val eventCount: Int,
    val hasDiary: Boolean,
    val gpsRunning: Boolean,
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val repository = app.eventRepository
    private val diaryRepository = app.diaryRepository

    val todayVisits = app.visitRepository.observeTodayVisits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEvents = repository.observeTodayEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(
        TodayUiState(
            cloudBrainReady = runCatching {
                app.settingsRepository.isCloudBrainReady()
            }.getOrDefault(false),
            gpsEnabled = runCatching {
                app.settingsRepository.get().gpsCaptureEnabled
            }.getOrDefault(false),
            gpsActive = runCatching { isGpsCaptureActive() }.getOrDefault(false),
        ),
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                todayVisits,
                todayEvents,
                diaryRepository.observeToday(),
                LocationService.running,
            ) { visits, events, diary, gpsRunning ->
                TodayDataState(
                    visitCount = visits.size,
                    eventCount = events.size,
                    hasDiary = !diary?.text.isNullOrBlank(),
                    gpsRunning = gpsRunning,
                )
            }.collect { data ->
                val settings = runCatching { app.settingsRepository.get() }.getOrElse { error ->
                    showError(error, "Unable to refresh capture status.")
                    return@collect
                }
                _uiState.update { current ->
                    current.copy(
                        visitCount = data.visitCount,
                        eventCount = data.eventCount,
                        hasDiary = data.hasDiary,
                        cloudBrainReady = runCatching {
                            app.settingsRepository.isCloudBrainReady()
                        }.getOrDefault(false),
                        gpsEnabled = settings.gpsCaptureEnabled,
                        gpsActive = settings.gpsCaptureEnabled &&
                            PermissionHelper.canCaptureLocation(getApplication()) &&
                            data.gpsRunning,
                    )
                }
            }
        }
    }

    fun addOptionalNote(text: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.addManualEvent(text) }.fold(
                onSuccess = {
                    clearError()
                    onSaved()
                },
                onFailure = { error -> showError(error, "Unable to save the note.") },
            )
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            runCatching { repository.deleteEvent(event) }
                .onFailure { error -> showError(error, "Unable to delete the event.") }
        }
    }

    fun markSignificantMoment() {
        viewModelScope.launch {
            runCatching {
                repository.addMomentMarker("Significant moment flagged (passive marker)")
                CaptureAuditLog.log(getApplication(), "moment", "User flagged significant moment")
            }.fold(
                onSuccess = { clearError() },
                onFailure = { error -> showError(error, "Unable to mark this moment.") },
            )
        }
    }

    fun recordVoiceNote() {
        if (_uiState.value.isRecordingVoice) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecordingVoice = true, voiceMessage = null, error = null)
            val result = runCatching { VoiceCaptureOrchestrator(app).captureAndSave() }
                .getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { transcript ->
                    _uiState.value = _uiState.value.copy(
                        isRecordingVoice = false,
                        voiceMessage = "Voice saved: ${transcript.take(80)}",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRecordingVoice = false,
                        error = error.message ?: "Voice capture failed.",
                    )
                },
            )
        }
    }

    fun onVoicePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            error = "Microphone permission is required for voice notes.",
        )
    }

    fun seedSyntheticDay() {
        if (_uiState.value.isSeeding) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeeding = true, seedMessage = null, error = null)
            runCatching { SyntheticDayGenerator.seedToday(app) }.fold(
                onSuccess = { result ->
                    CaptureAuditLog.log(
                        getApplication(),
                        "synthetic",
                        "Seeded ${result.visitsInserted} visits, ${result.eventsInserted} events",
                    )
                    _uiState.value = _uiState.value.copy(
                        isSeeding = false,
                        seedMessage = "Synthetic day loaded: ${result.visitsInserted} visits, " +
                            "${result.eventsInserted} events.",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isSeeding = false)
                    showError(error, "Unable to load the synthetic day.")
                },
            )
        }
    }

    fun generateAiReport() {
        if (_uiState.value.isGeneratingReport) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingReport = true, error = null)
            val result = runCatching {
                app.reportGenerator.generateAndSaveForDate(com.dailybeat.app.util.DateKeys.today())
            }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isGeneratingReport = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingReport = false,
                        error = error.message ?: "Report failed.",
                    )
                },
            )
        }
    }

    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshStatus() {
        runCatching {
            val settings = app.settingsRepository.get()
            _uiState.update { current ->
                current.copy(
                    cloudBrainReady = app.settingsRepository.isCloudBrainReady(),
                    gpsEnabled = settings.gpsCaptureEnabled,
                    gpsActive = isGpsCaptureActive(),
                )
            }
        }.onFailure { error -> showError(error, "Unable to refresh capture status.") }
    }

    private fun showError(error: Throwable, fallback: String) {
        _uiState.update { it.copy(error = error.message ?: fallback) }
    }

    private fun isGpsCaptureActive(): Boolean {
        val settings = app.settingsRepository.get()
        return settings.gpsCaptureEnabled &&
            PermissionHelper.canCaptureLocation(getApplication()) &&
            LocationService.isRunning
    }
}
