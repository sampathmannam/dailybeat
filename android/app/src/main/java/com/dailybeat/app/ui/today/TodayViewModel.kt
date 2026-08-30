package com.dailybeat.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.capture.VoiceCaptureOrchestrator
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
            cloudBrainReady = app.settingsRepository.isCloudBrainReady(),
            gpsEnabled = app.settingsRepository.get().gpsCaptureEnabled,
            gpsActive = isGpsCaptureActive(),
        ),
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                todayVisits,
                todayEvents,
                diaryRepository.observeToday(),
            ) { visits, events, diary ->
                TodayUiState(
                    visitCount = visits.size,
                    eventCount = events.size,
                    hasDiary = !diary?.text.isNullOrBlank(),
                    cloudBrainReady = app.settingsRepository.isCloudBrainReady(),
                    gpsEnabled = app.settingsRepository.get().gpsCaptureEnabled,
                    gpsActive = isGpsCaptureActive(),
                    isGeneratingReport = _uiState.value.isGeneratingReport,
                    isSeeding = _uiState.value.isSeeding,
                    seedMessage = _uiState.value.seedMessage,
                    isRecordingVoice = _uiState.value.isRecordingVoice,
                    voiceMessage = _uiState.value.voiceMessage,
                    error = _uiState.value.error,
                )
            }.collect { state ->
                if (!state.isGeneratingReport) {
                    _uiState.value = state
                }
            }
        }
    }

    fun addOptionalNote(text: String) {
        viewModelScope.launch {
            repository.addManualEvent(text)
            clearError()
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch { repository.deleteEvent(event) }
    }

    fun markSignificantMoment() {
        viewModelScope.launch {
            repository.addMomentMarker("Significant moment flagged (passive marker)")
            CaptureAuditLog.log(getApplication(), "moment", "User flagged significant moment")
            clearError()
        }
    }

    fun recordVoiceNote() {
        if (_uiState.value.isRecordingVoice) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecordingVoice = true, voiceMessage = null, error = null)
            VoiceCaptureOrchestrator(app).captureAndSave().fold(
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
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeeding = true, seedMessage = null, error = null)
            val result = SyntheticDayGenerator.seedToday(app)
            CaptureAuditLog.log(
                getApplication(),
                "synthetic",
                "Seeded ${result.visitsInserted} visits, ${result.eventsInserted} events",
            )
            _uiState.value = _uiState.value.copy(
                isSeeding = false,
                seedMessage = "Synthetic day loaded: ${result.visitsInserted} visits, ${result.eventsInserted} events.",
            )
        }
    }

    fun generateAiReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingReport = true, error = null)
            app.reportGenerator.generateAndSaveForDate(com.dailybeat.app.util.DateKeys.today()).fold(
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

    private fun isGpsCaptureActive(): Boolean {
        val settings = app.settingsRepository.get()
        return settings.gpsCaptureEnabled &&
            PermissionHelper.canCaptureLocation(getApplication())
    }
}
