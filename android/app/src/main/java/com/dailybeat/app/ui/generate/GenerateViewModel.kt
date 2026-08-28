package com.dailybeat.app.ui.generate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GenerateUiState(
    val eventsText: String = "",
    val output: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val modelAvailable: Boolean = false,
)

class GenerateViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val llm = app.llm

    private val _uiState = MutableStateFlow(
        GenerateUiState(modelAvailable = llm.isModelAvailable()),
    )
    val uiState: StateFlow<GenerateUiState> = _uiState.asStateFlow()

    fun setEventsText(text: String) {
        _uiState.value = _uiState.value.copy(eventsText = text, error = null)
    }

    fun generate() {
        val events = _uiState.value.eventsText.trim()
        if (events.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Enter events to convert.")
            return
        }
        if (!llm.isModelAvailable()) {
            _uiState.value = _uiState.value.copy(
                error = "GGUF model missing. Add dailybeat-q4_k_m.gguf to assets after fine-tune.",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, output = "")
            val result = llm.generateDairy(events)
            result.fold(
                onSuccess = { dairy ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        output = dairy,
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Generation failed.",
                    )
                },
            )
        }
    }
}
