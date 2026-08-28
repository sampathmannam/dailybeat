package com.dailybeat.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CallLogWorker
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.data.model.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val officerName: String = "",
    val gpsEnabled: Boolean = true,
    val callLogEnabled: Boolean = false,
    val modelImported: Boolean = false,
    val placeName: String = "",
    val placeLat: String = "",
    val placeLon: String = "",
    val places: List<Place> = emptyList(),
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val settings = app.settingsRepository.get()
        viewModelScope.launch {
            val places = app.placeRepository.all()
            _uiState.value = SettingsUiState(
                officerName = settings.officerName,
                gpsEnabled = settings.gpsCaptureEnabled,
                callLogEnabled = settings.callLogEnabled,
                modelImported = app.modelImporter.hasBundledOrLocalModel(),
                places = places,
            )
        }
    }

    fun setOfficerName(name: String) {
        app.settingsRepository.setOfficerName(name)
        _uiState.update { it.copy(officerName = name) }
    }

    fun setGpsEnabled(enabled: Boolean) {
        app.settingsRepository.setGpsEnabled(enabled)
        _uiState.update { it.copy(gpsEnabled = enabled) }
        CaptureController.applyFromSettings(app)
    }

    fun setCallLogEnabled(enabled: Boolean) {
        app.settingsRepository.setCallLogEnabled(enabled)
        _uiState.update { it.copy(callLogEnabled = enabled) }
        if (enabled) {
            CaptureController.applyFromSettings(app)
        } else {
            CallLogWorker.cancel(app)
        }
    }

    fun updatePlaceDraft(name: String, lat: String, lon: String) {
        _uiState.update { it.copy(placeName = name, placeLat = lat, placeLon = lon) }
    }

    fun addPlace() {
        val state = _uiState.value
        val lat = state.placeLat.toDoubleOrNull() ?: return
        val lon = state.placeLon.toDoubleOrNull() ?: return
        val name = state.placeName.trim()
        if (name.isEmpty()) return

        viewModelScope.launch {
            app.placeRepository.add(name, lat, lon)
            refresh()
            _uiState.update { it.copy(placeName = "", placeLat = "", placeLon = "") }
        }
    }

    fun deletePlace(place: Place) {
        viewModelScope.launch {
            app.placeRepository.delete(place)
            refresh()
        }
    }

    fun importModel() {
        val imported = app.modelImporter.importFromDownloads()
        _uiState.update { it.copy(modelImported = imported || app.modelImporter.hasBundledOrLocalModel()) }
    }
}
