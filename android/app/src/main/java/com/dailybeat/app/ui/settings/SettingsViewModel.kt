package com.dailybeat.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CallLogWorker
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.cloud.DayContextBuilder
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.settings.CloudProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val officerName: String = "",
    val gpsEnabled: Boolean = true,
    val callLogEnabled: Boolean = false,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = CloudProvider.OPENAI.id,
    val cloudModel: String = CloudProvider.OPENAI.defaultModel,
    val cloudBaseUrl: String = "",
    val apiKeyDraft: String = "",
    val hasApiKey: Boolean = false,
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
    val cloudTestResult: String? = null,
    val cloudTesting: Boolean = false,
    val modelImported: Boolean = false,
    val placeName: String = "",
    val placeLat: String = "",
    val placeLon: String = "",
    val places: List<Place> = emptyList(),
    val placeError: String? = null,
    val auditLines: List<String> = emptyList(),
    val syntheticResult: String? = null,
    val isSeedingSynthetic: Boolean = false,
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
        val current = _uiState.value
        viewModelScope.launch {
            val places = app.placeRepository.all()
            _uiState.value = SettingsUiState(
                officerName = settings.officerName,
                gpsEnabled = settings.gpsCaptureEnabled,
                callLogEnabled = settings.callLogEnabled,
                cloudLlmEnabled = settings.cloudLlmEnabled,
                cloudProvider = settings.cloudProvider,
                cloudModel = settings.cloudModel,
                cloudBaseUrl = settings.cloudBaseUrl,
                apiKeyDraft = current.apiKeyDraft,
                hasApiKey = app.settingsRepository.secureApiKey.hasApiKey(),
                autoEveningReport = settings.autoEveningReport,
                autoMiddayPulse = settings.autoMiddayPulse,
                modelImported = app.modelImporter.hasBundledOrLocalModel(),
                places = places,
                auditLines = CaptureAuditLog.readRecent(app),
            )
        }
    }

    fun loadAuditLog() {
        _uiState.update { it.copy(auditLines = CaptureAuditLog.readRecent(app)) }
    }

    fun setAutoMiddayPulse(enabled: Boolean) {
        app.settingsRepository.setAutoMiddayPulse(enabled)
        _uiState.update { it.copy(autoMiddayPulse = enabled) }
        if (enabled) {
            PulseScheduler.scheduleNext(app)
        } else {
            PulseScheduler.cancel(app)
        }
    }

    fun seedSyntheticData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSeedingSynthetic = true, syntheticResult = null) }
            val result = SyntheticDayGenerator.seedToday(app)
            CaptureAuditLog.log(app, "synthetic", "Settings seeded ${result.visitsInserted} visits")
            _uiState.update {
                it.copy(
                    isSeedingSynthetic = false,
                    syntheticResult = "Loaded ${result.visitsInserted} visits and ${result.eventsInserted} events.",
                    auditLines = CaptureAuditLog.readRecent(app),
                )
            }
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

    fun setCloudLlmEnabled(enabled: Boolean) {
        app.settingsRepository.setCloudLlmEnabled(enabled)
        _uiState.update { it.copy(cloudLlmEnabled = enabled) }
    }

    fun setCloudProvider(providerId: String) {
        app.settingsRepository.setCloudProvider(providerId)
        val provider = CloudProvider.entries.find { it.id == providerId } ?: CloudProvider.OPENAI
        app.settingsRepository.setCloudModel(provider.defaultModel)
        _uiState.update {
            it.copy(cloudProvider = providerId, cloudModel = provider.defaultModel)
        }
    }

    fun setCloudModel(model: String) {
        app.settingsRepository.setCloudModel(model)
        _uiState.update { it.copy(cloudModel = model) }
    }

    fun setCloudBaseUrl(url: String) {
        app.settingsRepository.setCloudBaseUrl(url)
        _uiState.update { it.copy(cloudBaseUrl = url) }
    }

    fun setApiKeyDraft(key: String) {
        _uiState.update { it.copy(apiKeyDraft = key, cloudTestResult = null) }
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKeyDraft.trim()
        if (key.isEmpty()) return
        app.settingsRepository.secureApiKey.setApiKey(key)
        _uiState.update { it.copy(apiKeyDraft = "", hasApiKey = true) }
    }

    fun setAutoEveningReport(enabled: Boolean) {
        app.settingsRepository.setAutoEveningReport(enabled)
        _uiState.update { it.copy(autoEveningReport = enabled) }
    }

    fun testCloudConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(cloudTesting = true, cloudTestResult = null) }
            val settings = app.settingsRepository.get()
            val draft = _uiState.value.apiKeyDraft.trim()
            if (draft.isNotEmpty()) {
                app.settingsRepository.secureApiKey.setApiKey(draft)
            }
            val result = app.cloudLlm.generate(
                settings,
                DayContextBuilder.SYSTEM_PROMPT,
                "Reply with exactly: DailyBeat cloud AI is connected.",
            )
            _uiState.update {
                it.copy(
                    cloudTesting = false,
                    cloudTestResult = result.fold(
                        onSuccess = { "Connected successfully." },
                        onFailure = { error -> error.message ?: "Connection failed." },
                    ),
                    hasApiKey = app.settingsRepository.secureApiKey.hasApiKey(),
                    apiKeyDraft = "",
                )
            }
        }
    }

    fun updatePlaceDraft(name: String, lat: String, lon: String) {
        _uiState.update { it.copy(placeName = name, placeLat = lat, placeLon = lon) }
    }

    fun addPlace() {
        val state = _uiState.value
        val name = state.placeName.trim()
        if (name.isEmpty()) {
            _uiState.update { it.copy(placeError = "Enter a place name.") }
            return
        }
        val lat = state.placeLat.toDoubleOrNull()
        val lon = state.placeLon.toDoubleOrNull()
        if (lat == null || lon == null) {
            _uiState.update { it.copy(placeError = "Enter valid latitude and longitude.") }
            return
        }

        viewModelScope.launch {
            app.placeRepository.add(name, lat, lon)
            refresh()
            _uiState.update { it.copy(placeName = "", placeLat = "", placeLon = "", placeError = null) }
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
