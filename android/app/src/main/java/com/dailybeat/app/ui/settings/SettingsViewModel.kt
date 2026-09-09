package com.dailybeat.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.domain.FrequentPlaceLearner
import com.dailybeat.app.domain.PlaceSuggestion
import com.dailybeat.app.cloud.CloudTokenBudgets
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val officerName: String = "",
    val supervisorName: String = "",
    val gpsEnabled: Boolean = true,
    val captureMessage: String? = null,
    val batteryUnrestricted: Boolean = true,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = CloudProvider.DEEPSEEK.id,
    val cloudModel: String = CloudProvider.DEEPSEEK.defaultModel,
    val cloudBaseUrl: String = "",
    val apiKeyDraft: String = "",
    val hasApiKey: Boolean = false,
    val apiKeyBusy: Boolean = false,
    val apiKeyRemovalConfirmation: Boolean = false,
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
    val cloudTestResult: String? = null,
    val cloudTesting: Boolean = false,
    val placeName: String = "",
    val placeLat: String = "",
    val placeLon: String = "",
    val places: List<Place> = emptyList(),
    val placeError: String? = null,
    val auditLines: List<String> = emptyList(),
    val syntheticResult: String? = null,
    val isSeedingSynthetic: Boolean = false,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val backupConfigured: Boolean = false,
    val backupEmailDraft: String = "",
    val backupPasswordDraft: String = "",
    val backupSignedInEmail: String? = null,
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val backupRestoreConfirmation: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var placeMutationInFlight = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val settings = app.settingsRepository.get()
                val places = app.placeRepository.all()
                val recentVisits = app.visitRepository.visitsLastDays(14)
                val suggestions = FrequentPlaceLearner.suggest(recentVisits, places)
                val hasKey = withContext(Dispatchers.IO) {
                    app.settingsRepository.secureApiKey.hasApiKey()
                }
                val auditLines = CaptureAuditLog.readRecent(app)
                // Copy rather than rebuild: a rebuild threw away whatever the officer was in the
                // middle of, such as a half-typed place or the result of a connection test.
                _uiState.update { current ->
                    current.copy(
                        officerName = settings.officerName,
                        supervisorName = settings.supervisorName,
                        gpsEnabled = settings.gpsCaptureEnabled,
                        captureMessage = captureStatusMessage(settings.gpsCaptureEnabled),
                        batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(app),
                        cloudLlmEnabled = settings.cloudLlmEnabled,
                        cloudProvider = settings.cloudProvider,
                        cloudModel = settings.cloudModel,
                        cloudBaseUrl = settings.cloudBaseUrl,
                        hasApiKey = hasKey,
                        autoEveningReport = settings.autoEveningReport,
                        autoMiddayPulse = settings.autoMiddayPulse,
                        places = places,
                        auditLines = auditLines,
                        placeSuggestions = suggestions,
                        backupConfigured = app.backupCoordinator.isConfigured,
                        backupSignedInEmail = app.backupCoordinator.currentSession()?.email,
                    )
                }
            } catch (error: Exception) {
                _uiState.update { current ->
                    current.copy(
                        backupBusy = false,
                        backupMessage = error.message ?: "Unable to load settings data.",
                    )
                }
            }
        }
    }

    fun setBackupEmail(email: String) {
        _uiState.update { it.copy(backupEmailDraft = email, backupMessage = null) }
    }

    fun setBackupPassword(password: String) {
        _uiState.update { it.copy(backupPasswordDraft = password, backupMessage = null) }
    }

    fun signInToBackup() {
        val state = _uiState.value
        if (state.backupBusy) return
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                app.backupCoordinator.signIn(state.backupEmailDraft, state.backupPasswordDraft)
            }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupSignedInEmail = session.email,
                            backupPasswordDraft = "",
                            backupMessage = "Signed in. Back up this phone now.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(backupBusy = false, backupMessage = error.message ?: "Unable to sign in.")
                    }
                },
            )
        }
    }

    fun createBackupAccount() {
        val state = _uiState.value
        if (state.backupBusy) return
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val result = runCatching {
                app.backupCoordinator.signUp(state.backupEmailDraft, state.backupPasswordDraft)
            }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { signUpResult ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupSignedInEmail = signUpResult.session?.email,
                            backupPasswordDraft = "",
                            backupMessage = if (signUpResult.requiresEmailConfirmation) {
                                "Account created. Confirm the email, then sign in."
                            } else {
                                "Account created. Back up this phone now."
                            },
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(backupBusy = false, backupMessage = error.message ?: "Unable to create account.")
                    }
                },
            )
        }
    }

    fun backupNow() {
        if (_uiState.value.backupBusy) return
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val result = runCatching { app.backupCoordinator.backupNow() }
                .getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(backupBusy = false, backupMessage = "Cloud backup completed.") }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(backupBusy = false, backupMessage = error.message ?: "Cloud backup failed.")
                    }
                },
            )
        }
    }

    fun requestBackupRestore() {
        _uiState.update { it.copy(backupRestoreConfirmation = true, backupMessage = null) }
    }

    fun cancelBackupRestore() {
        _uiState.update { it.copy(backupRestoreConfirmation = false) }
    }

    fun confirmBackupRestore() {
        if (_uiState.value.backupBusy) return
        _uiState.update {
            it.copy(backupBusy = true, backupRestoreConfirmation = false, backupMessage = null)
        }
        viewModelScope.launch {
            val result = runCatching { app.backupCoordinator.restoreNow() }
                .getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = {
                    val activationError = runCatching {
                        CaptureController.applyFromSettings(app)
                        if (app.settingsRepository.get().autoMiddayPulse) {
                            PulseScheduler.scheduleNext(app)
                        } else {
                            PulseScheduler.cancel(app)
                        }
                    }.exceptionOrNull()
                    if (activationError != null) {
                        _uiState.update {
                            it.copy(
                                backupBusy = false,
                                backupMessage = activationError.message
                                    ?: "Backup restored, but capture could not be restarted.",
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                backupBusy = false,
                                backupMessage = "Cloud backup restored on this phone.",
                            )
                        }
                    }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(backupBusy = false, backupMessage = error.message ?: "Cloud restore failed.")
                    }
                },
            )
        }
    }

    fun signOutOfBackup() {
        if (_uiState.value.backupBusy) return
        app.backupCoordinator.signOut()
        _uiState.update {
            it.copy(
                backupSignedInEmail = null,
                backupPasswordDraft = "",
                backupMessage = "Signed out. Local DailyBeat data remains on this phone.",
                backupRestoreConfirmation = false,
            )
        }
    }

    fun setSupervisorName(name: String) {
        app.settingsRepository.setSupervisorName(name)
        _uiState.update { it.copy(supervisorName = name) }
    }

    fun addSuggestedPlace(suggestion: PlaceSuggestion) {
        if (placeMutationInFlight) return
        placeMutationInFlight = true
        viewModelScope.launch {
            runCatching {
                app.placeRepository.add(suggestion.name, suggestion.latitude, suggestion.longitude)
            }.fold(
                onSuccess = {
                    placeMutationInFlight = false
                    refresh()
                },
                onFailure = { error ->
                    placeMutationInFlight = false
                    _uiState.update {
                        it.copy(placeError = error.message ?: "Unable to save the suggested place.")
                    }
                },
            )
        }
    }

    fun loadAuditLog() {
        viewModelScope.launch {
            val lines = CaptureAuditLog.readRecent(app)
            _uiState.update { it.copy(auditLines = lines) }
        }
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
        if (_uiState.value.isSeedingSynthetic) return
        _uiState.update { it.copy(isSeedingSynthetic = true, syntheticResult = null) }
        viewModelScope.launch {
            runCatching { SyntheticDayGenerator.seedToday(app) }.fold(
                onSuccess = { result ->
                    CaptureAuditLog.log(app, "synthetic", "Settings seeded ${result.visitsInserted} visits")
                    _uiState.update {
                        it.copy(
                            isSeedingSynthetic = false,
                            syntheticResult = "Loaded ${result.visitsInserted} visits and ${result.eventsInserted} events.",
                            auditLines = CaptureAuditLog.readRecent(app),
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSeedingSynthetic = false,
                            syntheticResult = error.message ?: "Unable to load synthetic data.",
                        )
                    }
                },
            )
        }
    }

    fun setOfficerName(name: String) {
        app.settingsRepository.setOfficerName(name)
        _uiState.update { it.copy(officerName = name) }
    }

    fun setGpsEnabled(enabled: Boolean) {
        app.settingsRepository.setGpsEnabled(enabled)
        _uiState.update {
            it.copy(gpsEnabled = enabled, captureMessage = captureStatusMessage(enabled))
        }
        CaptureController.applyFromSettings(app)
    }

    fun setCloudLlmEnabled(enabled: Boolean) {
        app.settingsRepository.setCloudLlmEnabled(enabled)
        _uiState.update { it.copy(cloudLlmEnabled = enabled) }
        if (enabled) PulseScheduler.scheduleNext(app) else PulseScheduler.cancel(app)
    }

    fun setCloudProvider(providerId: String) {
        app.settingsRepository.setCloudProvider(providerId)
        val provider = CloudProvider.entries.find { it.id == providerId } ?: CloudProvider.DEEPSEEK
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
        if (key.isEmpty() || _uiState.value.apiKeyBusy) return
        _uiState.update { it.copy(apiKeyBusy = true, cloudTestResult = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    app.settingsRepository.secureApiKey.setApiKey(key)
                }
            }.fold(
                onSuccess = {
                    PulseScheduler.scheduleNext(app)
                    _uiState.update {
                        it.copy(
                            apiKeyDraft = "",
                            hasApiKey = true,
                            apiKeyBusy = false,
                            cloudTestResult = "API key saved securely.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            apiKeyBusy = false,
                            cloudTestResult = error.message ?: "Unable to save the API key securely.",
                        )
                    }
                },
            )
        }
    }

    fun requestApiKeyRemoval() {
        if (_uiState.value.hasApiKey && !_uiState.value.apiKeyBusy) {
            _uiState.update { it.copy(apiKeyRemovalConfirmation = true, cloudTestResult = null) }
        }
    }

    fun cancelApiKeyRemoval() {
        _uiState.update { it.copy(apiKeyRemovalConfirmation = false) }
    }

    fun confirmApiKeyRemoval() {
        if (_uiState.value.apiKeyBusy || !_uiState.value.hasApiKey) return
        _uiState.update {
            it.copy(apiKeyBusy = true, apiKeyRemovalConfirmation = false, cloudTestResult = null)
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    app.settingsRepository.secureApiKey.clearApiKey()
                }
            }.fold(
                onSuccess = {
                    PulseScheduler.cancel(app)
                    _uiState.update {
                        it.copy(
                            apiKeyDraft = "",
                            hasApiKey = false,
                            apiKeyBusy = false,
                            cloudTestResult = "API key removed from this phone. Cloud requests are stopped.",
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            apiKeyBusy = false,
                            cloudTestResult = error.message
                                ?: "Unable to remove the API key securely.",
                        )
                    }
                },
            )
        }
    }

    fun setAutoEveningReport(enabled: Boolean) {
        app.settingsRepository.setAutoEveningReport(enabled)
        _uiState.update { it.copy(autoEveningReport = enabled) }
    }

    fun testCloudConnection() {
        if (_uiState.value.cloudTesting) return
        _uiState.update { it.copy(cloudTesting = true, cloudTestResult = null) }
        viewModelScope.launch {
            try {
                val settings = app.settingsRepository.get()
                val draft = _uiState.value.apiKeyDraft.trim()
                if (draft.isNotEmpty()) {
                    val saveError = withContext(Dispatchers.IO) {
                        runCatching {
                            app.settingsRepository.secureApiKey.setApiKey(draft)
                        }.exceptionOrNull()
                    }
                    if (saveError != null) {
                        _uiState.update {
                            it.copy(
                                cloudTesting = false,
                                cloudTestResult = saveError.message
                                    ?: "Unable to save the API key securely.",
                            )
                        }
                        return@launch
                    }
                    PulseScheduler.scheduleNext(app)
                }
                val result = app.cloudLlm.generate(
                    settings,
                    "You are a connection test. Return only the requested confirmation text.",
                    "Reply with exactly: DailyBeat cloud AI is connected.",
                    maxOutputTokens = CloudTokenBudgets.CONNECTION,
                )
                val hasApiKey = withContext(Dispatchers.IO) {
                    app.settingsRepository.secureApiKey.hasApiKey()
                }
                _uiState.update {
                    it.copy(
                        cloudTesting = false,
                        cloudTestResult = result.fold(
                            onSuccess = { "Connected successfully." },
                            onFailure = { error -> error.message ?: "Connection failed." },
                        ),
                        hasApiKey = hasApiKey,
                        apiKeyDraft = "",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        cloudTesting = false,
                        cloudTestResult = error.message ?: "Connection failed.",
                    )
                }
            }
        }
    }

    fun updatePlaceDraft(name: String, lat: String, lon: String) {
        _uiState.update { it.copy(placeName = name, placeLat = lat, placeLon = lon) }
    }

    fun addPlace() {
        if (placeMutationInFlight) return
        val state = _uiState.value
        val name = state.placeName.trim()
        val validationError = PlaceInputValidator.errorFor(name, state.placeLat, state.placeLon)
        if (validationError != null) {
            _uiState.update { it.copy(placeError = validationError) }
            return
        }
        val lat = state.placeLat.toDouble()
        val lon = state.placeLon.toDouble()

        placeMutationInFlight = true
        viewModelScope.launch {
            runCatching { app.placeRepository.add(name, lat, lon) }.fold(
                onSuccess = {
                    placeMutationInFlight = false
                    _uiState.update {
                        it.copy(placeName = "", placeLat = "", placeLon = "", placeError = null)
                    }
                    refresh()
                },
                onFailure = { error ->
                    placeMutationInFlight = false
                    _uiState.update {
                        it.copy(placeError = error.message ?: "Unable to add this place.")
                    }
                },
            )
        }
    }

    fun deletePlace(place: Place) {
        if (placeMutationInFlight) return
        placeMutationInFlight = true
        viewModelScope.launch {
            runCatching { app.placeRepository.delete(place) }.fold(
                onSuccess = {
                    placeMutationInFlight = false
                    // Reflect the completed delete immediately. A full refresh still follows for
                    // suggestions and other derived state, but the stale row must not remain
                    // tappable while that second database read is waiting to run.
                    _uiState.update { state ->
                        state.copy(
                            places = state.places.filterNot { candidate -> candidate.id == place.id },
                            placeError = null,
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    placeMutationInFlight = false
                    _uiState.update {
                        it.copy(placeError = error.message ?: "Unable to delete this place.")
                    }
                },
            )
        }
    }

    private fun captureStatusMessage(enabled: Boolean): String? = when {
        !enabled -> null
        !PermissionHelper.hasLocation(app) ->
            "Location permission is off. Open Android app settings and allow location."
        !PermissionHelper.hasBackgroundLocation(app) ->
            "For reliable passive capture, allow location all the time in Android app settings."
        !PermissionHelper.hasNotifications(app) ->
            "Notifications are off. Enable them so Android can show capture status."
        else -> null
    }

}
