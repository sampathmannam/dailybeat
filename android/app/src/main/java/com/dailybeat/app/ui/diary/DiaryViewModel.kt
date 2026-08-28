package com.dailybeat.app.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.domain.DairyFormatter
import com.dailybeat.app.llm.buildDairyPrompt
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DiaryUiState(
    val date: LocalDate = DateKeys.today(),
    val text: String = "",
    val customEvents: String = "",
    val isGenerating: Boolean = false,
    val eventCount: Int = 0,
    val modelAvailable: Boolean = false,
    val error: String? = null,
)

class DiaryViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val date: LocalDate = DateKeys.parseOrToday(savedStateHandle.get<String>("dateKey"))

    private val _uiState = MutableStateFlow(
        DiaryUiState(
            date = date,
            modelAvailable = app.llm.isModelAvailable(),
        ),
    )
    val uiState: StateFlow<DiaryUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null

    val eventsForDay = app.eventRepository.observeEventsForDate(date)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(
                eventsForDay,
                app.diaryRepository.observeForDate(date),
            ) { events, diary ->
                val current = _uiState.value
                DiaryUiState(
                    date = date,
                    text = if (current.text.isBlank() && !diary?.text.isNullOrBlank()) {
                        diary!!.text
                    } else {
                        current.text
                    },
                    customEvents = current.customEvents,
                    isGenerating = current.isGenerating,
                    eventCount = events.size,
                    modelAvailable = app.llm.isModelAvailable(),
                    error = current.error,
                )
            }.collect { merged ->
                if (!merged.isGenerating) {
                    _uiState.value = merged
                } else {
                    _uiState.value = _uiState.value.copy(
                        eventCount = merged.eventCount,
                        modelAvailable = merged.modelAvailable,
                    )
                }
            }
        }
        viewModelScope.launch {
            val saved = app.diaryRepository.textForDate(date)
            if (!saved.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(text = saved)
            }
        }
    }

    fun updateCustomEvents(text: String) {
        _uiState.value = _uiState.value.copy(customEvents = text, error = null)
    }

    fun updateDiaryText(text: String) {
        _uiState.value = _uiState.value.copy(text = text, error = null)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            app.diaryRepository.saveForDate(date, text)
        }
    }

    fun generateFromLoggedEvents() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            app.dairyGenerator.generateForDay(date).fold(
                onSuccess = { dairy ->
                    _uiState.value = _uiState.value.copy(isGenerating = false, text = dairy)
                    app.diaryRepository.saveForDate(date, dairy)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = error.message ?: "Dairy generation failed.",
                    )
                },
            )
        }
    }

    fun generateFromCustomEvents() {
        val eventsText = _uiState.value.customEvents.trim()
        if (eventsText.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Enter events to convert.")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, error = null)
            val result = if (app.llm.isModelAvailable()) {
                app.llm.generate(buildDairyPrompt(eventsText))
            } else {
                val pseudo = eventsText.lines()
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, line ->
                        Event(
                            timestamp = System.currentTimeMillis() + index,
                            type = "manual",
                            rawText = line.trim(),
                        )
                    }
                Result.success(DairyFormatter.formatEvents(pseudo))
            }
            result.fold(
                onSuccess = { dairy ->
                    _uiState.value = _uiState.value.copy(isGenerating = false, text = dairy)
                    app.diaryRepository.saveForDate(date, dairy)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        error = error.message ?: "Generation failed.",
                    )
                },
            )
        }
    }

    fun exportPdfPath(): String? {
        val dairy = _uiState.value.text.trim()
        if (dairy.isEmpty()) return null
        val officer = app.settingsRepository.get().officerName
        return app.pdfExporter.exportDairy(officer, dairy, date).absolutePath
    }
}
