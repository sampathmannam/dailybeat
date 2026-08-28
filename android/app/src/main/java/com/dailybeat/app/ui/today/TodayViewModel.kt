package com.dailybeat.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.SpeechTranscriber
import com.dailybeat.app.capture.VoiceRecorder
import com.dailybeat.app.capture.VoiceTranscriptProvider
import com.dailybeat.app.capture.WhisperBridge
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayUiState(
    val eventCount: Int = 0,
    val hasDiary: Boolean = false,
    val modelAvailable: Boolean = false,
    val error: String? = null,
)

class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val repository = app.eventRepository
    private val diaryRepository = app.diaryRepository
    private val voiceRecorder = VoiceRecorder(application)
    private val whisperBridge = WhisperBridge(application)
    private val speechTranscriber = SpeechTranscriber(application)

    val todayEvents = repository.observeTodayEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _uiState = MutableStateFlow(
        TodayUiState(modelAvailable = app.llm.isModelAvailable()),
    )
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                todayEvents,
                diaryRepository.observeToday(),
            ) { events, diary ->
                TodayUiState(
                    eventCount = events.size,
                    hasDiary = !diary?.text.isNullOrBlank(),
                    modelAvailable = app.llm.isModelAvailable(),
                    error = _uiState.value.error,
                )
            }.collect { state ->
                _uiState.value = state.copy(error = _uiState.value.error)
            }
        }
    }

    fun addManualEvent(text: String) {
        viewModelScope.launch {
            repository.addManualEvent(text)
            clearError()
        }
    }

    fun deleteEvent(event: Event) {
        viewModelScope.launch {
            repository.deleteEvent(event)
        }
    }

    fun captureVoiceNote() {
        viewModelScope.launch {
            _isRecording.value = true
            clearError()
            try {
                if (!PermissionHelper.hasRecordAudio(app)) {
                    setError("Microphone permission required.")
                    return@launch
                }

                val transcript = resolveTranscript()
                if (transcript.isEmpty()) {
                    setError("No speech detected. Type a manual note or check speech settings.")
                    return@launch
                }
                val structured = app.eventExtractor.extract(transcript)
                repository.addStructuredEvent(structured, type = "voice")
            } catch (error: Exception) {
                setError(error.message ?: "Voice capture failed.")
            } finally {
                _isRecording.value = false
            }
        }
    }

    private suspend fun resolveTranscript(): String {
        VoiceTranscriptProvider.emulatorDemoTranscript()?.let { return it }

        if (whisperBridge.isModelAvailable()) {
            val samples = voiceRecorder.recordUntilSilence(maxSeconds = 8)
            val whisperText = whisperBridge.transcribe(samples).trim()
            if (whisperText.isNotEmpty()) return whisperText
        }

        if (speechTranscriber.isAvailable()) {
            val speechText = speechTranscriber.transcribe().trim()
            if (speechText.isNotEmpty()) return speechText
        }

        val samples = voiceRecorder.recordUntilSilence(maxSeconds = 8)
        return whisperBridge.transcribe(samples).trim()
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    private fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
