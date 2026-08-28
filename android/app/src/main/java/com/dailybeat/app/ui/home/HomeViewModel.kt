package com.dailybeat.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.VoiceRecorder
import com.dailybeat.app.capture.VoiceTranscriptProvider
import com.dailybeat.app.capture.WhisperBridge
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DairyUiState(
    val text: String = "",
    val isGenerating: Boolean = false,
    val error: String? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val repository = app.eventRepository
    private val diaryRepository = app.diaryRepository
    private val voiceRecorder = VoiceRecorder(application)
    private val whisperBridge = WhisperBridge(application)
    private var saveJob: Job? = null

    val todayEvents = repository.observeTodayEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dairyState = MutableStateFlow(DairyUiState())
    val dairyState: StateFlow<DairyUiState> = _dairyState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    val modelAvailable: Boolean = app.llm.isModelAvailable()

    init {
        viewModelScope.launch {
            val saved = diaryRepository.todayText()
            if (!saved.isNullOrBlank()) {
                _dairyState.update { it.copy(text = saved) }
            }
        }
    }

    fun addManualEvent(text: String) {
        viewModelScope.launch { repository.addManualEvent(text) }
    }

    suspend fun todayEventsText(): String = repository.todayEventsText()

    fun generateTodayDairy() {
        viewModelScope.launch {
            _dairyState.update { it.copy(isGenerating = true, error = null) }
            app.dairyGenerator.generateForToday().fold(
                onSuccess = { dairy ->
                    _dairyState.update { it.copy(isGenerating = false, text = dairy) }
                    diaryRepository.saveToday(dairy)
                },
                onFailure = { error ->
                    _dairyState.update {
                        it.copy(
                            isGenerating = false,
                            error = error.message ?: "Dairy generation failed.",
                        )
                    }
                },
            )
        }
    }

    fun updateDairyText(text: String) {
        _dairyState.update { it.copy(text = text, error = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            diaryRepository.saveToday(text)
        }
    }

    fun captureVoiceNote() {
        viewModelScope.launch {
            _isRecording.value = true
            _dairyState.update { it.copy(error = null) }
            try {
                if (!PermissionHelper.hasRecordAudio(app)) {
                    _dairyState.update { it.copy(error = "Microphone permission required.") }
                    return@launch
                }

                val transcript = when {
                    VoiceTranscriptProvider.emulatorDemoTranscript() != null -> {
                        VoiceTranscriptProvider.emulatorDemoTranscript()!!
                    }
                    else -> {
                        val samples = voiceRecorder.recordUntilSilence(maxSeconds = 8)
                        whisperBridge.transcribe(samples).trim()
                    }
                }

                if (transcript.isEmpty()) {
                    _dairyState.update { it.copy(error = "No speech detected. Type a manual note instead.") }
                    return@launch
                }
                val structured = app.eventExtractor.extract(transcript)
                repository.addStructuredEvent(structured, type = "voice")
            } catch (error: Exception) {
                _dairyState.update {
                    it.copy(error = error.message ?: "Voice capture failed.")
                }
            } finally {
                _isRecording.value = false
            }
        }
    }

    fun exportPdfPath(): String? {
        val dairy = _dairyState.value.text.trim()
        if (dairy.isEmpty()) return null
        val officer = app.settingsRepository.get().officerName
        return app.pdfExporter.exportDairy(officer, dairy).absolutePath
    }
}
