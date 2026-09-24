package com.dailybeat.app.ui.settings

import com.dailybeat.app.data.settings.JournalProfile
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.CaptureResumeWorker
import com.dailybeat.app.capture.CaptureStorageGate
import androidx.room.withTransaction
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.domain.FrequentPlaceLearner
import com.dailybeat.app.domain.PlaceSuggestion
import com.dailybeat.app.cloud.CloudTokenBudgets
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.data.settings.ThemePreference
import com.dailybeat.app.data.settings.SecureApiKeyStore
import com.dailybeat.app.cloud.CloudTextGenerator
import com.dailybeat.app.cloud.ReportRetryWorker
import com.dailybeat.app.backup.RestoreSettingsException
import com.dailybeat.app.util.PermissionHelper
import com.dailybeat.app.util.InputPolicy
import com.dailybeat.app.util.fetchCurrentLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dailybeat.app.util.userMessage
import com.dailybeat.app.data.retention.HistoryRetentionWorker

data class SettingsUiState(
    val geocodingEndpoint: String = "",
    val geocodingMessage: String? = null,
    val journalProfile: JournalProfile = JournalProfile.PERSONAL,
    val officerName: String = "",
    val supervisorName: String = "",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val gpsEnabled: Boolean = true,
    val captureMessage: String? = null,
    val screenError: String? = null,
    val capturePausedUntilMs: Long = 0L,
    val batteryUnrestricted: Boolean = true,
    val cloudLlmEnabled: Boolean = false,
    val cloudProvider: String = CloudProvider.DEEPSEEK.id,
    val cloudModel: String = CloudProvider.DEEPSEEK.defaultModel,
    val cloudBaseUrl: String = "",
    val apiKeyDraft: String = "",
    val hasApiKey: Boolean = false,
    val apiKeyBusy: Boolean = false,
    val apiKeyRemovalConfirmation: Boolean = false,
    val autoEveningReport: Boolean = false,
    val autoMiddayPulse: Boolean = false,
    val cloudTestResult: String? = null,
    val cloudTestIsError: Boolean = false,
    val cloudTesting: Boolean = false,
    val placeName: String = "",
    val placeLat: String = "",
    val placeLon: String = "",
    val placeLocating: Boolean = false,
    val placeBusy: Boolean = false,
    val places: List<Place> = emptyList(),
    val placeError: String? = null,
    val auditLines: List<String> = emptyList(),
    val syntheticResult: String? = null,
    val isSeedingSynthetic: Boolean = false,
    val placeSuggestions: List<PlaceSuggestion> = emptyList(),
    val backupVersions: List<com.dailybeat.app.backup.BackupVersion> = emptyList(),
    val selectedBackupVersion: String? = null,
    val backupConfigured: Boolean = false,
    val backupEmailDraft: String = "",
    val backupPasswordDraft: String = "",
    val recoveryPassphrase: String = "",
    val recoveryConfirmation: String = "",
    val legacyBackupRestore: Boolean = false,
    val backupSignedInEmail: String? = null,
    val backupBusy: Boolean = false,
    val backupMessage: String? = null,
    val backupMessageIsError: Boolean = false,
    val backupRestoreConfirmation: Boolean = false,
    val historyRetentionDays: Int = 0,
    val pendingRetentionDays: Int? = null,
    val cloudDeleteConfirmation: Boolean = false,
    val accountDeleteConfirmation: Boolean = false,
    val accountDeletePassword: String = "",
    val localEraseConfirmation: Boolean = false,
    val dataBusy: Boolean = false,
    val dataMessage: String? = null,
    val dataMessageIsError: Boolean = false,
    val localDataErased: Boolean = false,
)

class SettingsViewModel internal constructor(
    application: Application,
    private val secureApiKey: SecureApiKeyStore,
    private val cloudConnection: CloudTextGenerator,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application,
        (application as DailyBeatApp).settingsRepository.secureApiKey, application.cloudLlm)

    private val app = application as DailyBeatApp

    private val _uiState = MutableStateFlow(
        SettingsUiState(themePreference = app.settingsRepository.themePreference.value),
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var placesDataGeneration = CaptureStorageGate.dataGeneration.get()
    private var refreshSequence = 0L
    private var apiKeyDraftGeneration: Long? = null
    private var apiKeyRemovalGeneration: Long? = null
    private var geocodingDraftGeneration: Long? = null
    private var backupDraftGeneration: Long? = null
    private var apiKeyDraftRevision = 0L
    private var cloudConsentRevision = 0L
    private var geocodingSaveRevision = 0L
    private var eveningConsentRevision = 0L
    private var cloudConnectionJob: Job? = null
    private val credentialJobs = java.util.concurrent.ConcurrentHashMap.newKeySet<Job>()
    init {
        viewModelScope.launch {
            CaptureStorageGate.dataChanges.collect {
                val generation = CaptureStorageGate.dataGeneration.get()
                if (generation != placesDataGeneration) {
                    clearReplacedPlaceState(generation)
                    refresh()
                }
            }
        }
        refresh()
    }

    private fun clearReplacedPlaceState(generation: Long) {
        placesDataGeneration = generation
        refreshSequence++
        apiKeyDraftGeneration = null
        apiKeyRemovalGeneration = null
        geocodingDraftGeneration = null
        backupDraftGeneration = null
        apiKeyDraftRevision++
        cloudConsentRevision++
        geocodingSaveRevision++
        eveningConsentRevision++
        cloudConnectionJob = null
        credentialJobs.toList().forEach { it.cancel() }
        _uiState.update { it.copy(placeName = "", placeLat = "", placeLon = "",
            placeLocating = false, placeBusy = false, places = emptyList(),
            placeSuggestions = emptyList(), auditLines = emptyList(),
            apiKeyDraft = "", apiKeyBusy = false, apiKeyRemovalConfirmation = false, hasApiKey = false,
            cloudTesting = false, cloudTestResult = null, cloudLlmEnabled = false,
            cloudModel = "", cloudBaseUrl = "", geocodingEndpoint = "",
            backupEmailDraft = "", backupPasswordDraft = "", recoveryPassphrase = "",
            recoveryConfirmation = "", accountDeletePassword = "", backupSignedInEmail = null,
            backupVersions = emptyList(), selectedBackupVersion = null, backupRestoreConfirmation = false,
            cloudDeleteConfirmation = false, accountDeleteConfirmation = false,
            backupBusy = false, backupMessage = null, legacyBackupRestore = false,
            placeError = "Local data changed. Review your saved places again.") }
    }

    private fun currentPlaceGeneration(): Long? {
        val generation = CaptureStorageGate.dataGeneration.get()
        if (generation != placesDataGeneration) {
            clearReplacedPlaceState(generation)
            refresh()
            return null
        }
        return generation
    }

    private fun placeGenerationIsCurrent(generation: Long): Boolean =
        generation == placesDataGeneration && generation == CaptureStorageGate.dataGeneration.get()

    /** Track cancellable connection/key jobs, never a restore that intentionally changes data. */
    private fun launchCredentialJob(generation: Long, block: suspend () -> Unit): Job {
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            if (placeGenerationIsCurrent(generation)) block()
        }
        credentialJobs += job
        job.invokeOnCompletion { credentialJobs -= job }
        job.start()
        return job
    }

    private fun changeCloudSettings(change: () -> Unit, update: (SettingsUiState) -> SettingsUiState) {
        val generation = currentPlaceGeneration() ?: return
        launchCredentialJob(generation) {
            try {
                CaptureStorageGate.writeIfCurrent(generation) {
                    change()
                    refreshSequence++
                    _uiState.update(update)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (placeGenerationIsCurrent(generation)) _uiState.update {
                    it.copy(cloudTestResult = error.userMessage("Unable to update cloud settings."), cloudTestIsError = true)
                }
            }
        }
    }

    fun setGeocodingDraft(value: String) {
        geocodingDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(geocodingEndpoint = value.take(2048), geocodingMessage = null) }
    }
    fun saveGeocodingEndpoint() {
        val endpoint = _uiState.value.geocodingEndpoint.trim()
        val revision = ++geocodingSaveRevision
        // Withdrawal must reach the geocoder's live consent check immediately, even while
        // capture holds its storage lock. An older queued save cannot re-enable it afterward.
        if (endpoint.isEmpty()) {
            runCatching { app.settingsRepository.setGeocodingEndpoint("") }.fold(
                onSuccess = { _uiState.update { it.copy(geocodingEndpoint = "", geocodingMessage = "Place lookup turned off.") } },
                onFailure = { error -> _uiState.update { it.copy(geocodingMessage = error.userMessage("Could not turn off place lookup.")) } },
            )
            return
        }
        val generation = geocodingDraftGeneration ?: return
        if (currentPlaceGeneration() != generation) return
        launchCredentialJob(generation) {
            try {
                CaptureStorageGate.writeIfCurrent(generation) {
                    if (revision == geocodingSaveRevision) app.settingsRepository.setGeocodingEndpoint(endpoint)
                }
                if (placeGenerationIsCurrent(generation) && revision == geocodingSaveRevision) _uiState.update {
                    it.copy(geocodingMessage = "Place lookup setting saved.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (placeGenerationIsCurrent(generation) && revision == geocodingSaveRevision) _uiState.update {
                    it.copy(geocodingMessage = error.userMessage("Could not save the endpoint."))
                }
            }
        }
    }

    fun setJournalProfile(profile: JournalProfile) {
        app.settingsRepository.setJournalProfile(profile)
        _uiState.update { it.copy(journalProfile = profile) }
    }

    fun refresh() {
        val generation = CaptureStorageGate.dataGeneration.get()
        val sequence = ++refreshSequence
        viewModelScope.launch {
            try {
                val (places, recentVisits) = CaptureStorageGate.writeIfCurrent(generation) {
                    app.db.withTransaction {
                        app.placeRepository.all() to app.visitRepository.visitsLastDays(14)
                    }
                }
                val suggestions = FrequentPlaceLearner.suggest(recentVisits, places)
                val hasKey = withContext(Dispatchers.IO) {
                    secureApiKey.hasApiKey()
                }
                val auditLines = CaptureAuditLog.readRecent(app)
                if (generation != CaptureStorageGate.dataGeneration.get() || sequence != refreshSequence) return@launch
                // Read preferences after the slower database and secure-store work. Because this
                // coroutine and UI setters resume on the main thread, no tap can interleave
                // between this read and the state update and be replaced by stale settings.
                val settings = app.settingsRepository.get()
                // Copy rather than rebuild: a rebuild threw away whatever the officer was in the
                // middle of, such as a half-typed place or the result of a connection test.
                _uiState.update { current ->
                    current.copy(
                        geocodingEndpoint = app.settingsRepository.geocodingEndpoint(),
                        officerName = settings.officerName,
                        journalProfile = settings.journalProfile,
                        supervisorName = settings.supervisorName,
                        themePreference = settings.themePreference,
                        gpsEnabled = settings.gpsCaptureEnabled,
                        captureMessage = captureStatusMessage(settings.gpsCaptureEnabled),
                        capturePausedUntilMs = app.settingsRepository.capturePausedUntilMs(),
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
                        historyRetentionDays = settings.historyRetentionDays,
                        screenError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation != CaptureStorageGate.dataGeneration.get() || sequence != refreshSequence) return@launch
                _uiState.update { current ->
                    current.copy(
                        backupBusy = false,
                        screenError = error.userMessage("Unable to load settings. Try again."),
                    )
                }
            }
        }
    }

    fun setBackupEmail(email: String) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update {
            it.copy(
                backupEmailDraft = InputPolicy.singleLine(email, InputPolicy.BACKUP_EMAIL_CHARS),
                backupMessage = null,
            )
        }
    }

    fun setBackupPassword(password: String) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update {
            it.copy(
                backupPasswordDraft = InputPolicy.bounded(password, InputPolicy.BACKUP_PASSWORD_CHARS),
                backupMessage = null,
            )
        }
    }

    fun signInToBackup() {
        val generation = backupDraftGeneration ?: return
        if (currentPlaceGeneration() != generation) return
        val state = _uiState.value
        if (state.backupBusy || state.dataBusy) return
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        launchCredentialJob(generation) {
            val result = runCatching {
                app.backupCoordinator.signIn(state.backupEmailDraft, state.backupPasswordDraft)
            }.getOrElse { Result.failure(it) }
            if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupSignedInEmail = session.email,
                            backupPasswordDraft = "",
                            backupMessage = "Signed in. Back up this phone now.",
                            backupMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupMessage = error.userMessage("Unable to sign in."),
                            backupMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun createBackupAccount() {
        val generation = backupDraftGeneration ?: return
        if (currentPlaceGeneration() != generation) return
        val state = _uiState.value
        if (state.backupBusy || state.dataBusy) return
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        launchCredentialJob(generation) {
            val result = runCatching {
                app.backupCoordinator.signUp(state.backupEmailDraft, state.backupPasswordDraft)
            }.getOrElse { Result.failure(it) }
            if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
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
                            backupMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupMessage = error.userMessage("Unable to create account."),
                            backupMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun setRecoveryPassphrase(value: String) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(recoveryPassphrase = InputPolicy.bounded(value, 256)) }
    }
    fun setRecoveryConfirmation(value: String) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(recoveryConfirmation = InputPolicy.bounded(value, 256)) }
    }
    fun setLegacyBackupRestore(value: Boolean) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(legacyBackupRestore = value) }
    }

    fun backupNow() {
        val generation = backupDraftGeneration ?: currentPlaceGeneration() ?: return
        if (currentPlaceGeneration() != generation) return
        if (_uiState.value.backupBusy || _uiState.value.dataBusy) return
        val current = _uiState.value
        if (current.recoveryPassphrase.length < 20 || current.recoveryPassphrase != current.recoveryConfirmation) {
            _uiState.update { it.copy(backupMessage = "Enter a recovery passphrase of at least 20 characters and confirm it.", backupMessageIsError = true) }
            return
        }
        val passphrase = current.recoveryPassphrase.toCharArray()
        _uiState.update { it.copy(recoveryPassphrase = "", recoveryConfirmation = "") }
        _uiState.update { it.copy(backupBusy = true, backupMessage = null) }
        viewModelScope.launch {
            val result = try {
                if (!placeGenerationIsCurrent(generation)) return@launch
                runCatching { app.backupCoordinator.backupNow(passphrase) }.getOrElse { Result.failure(it) }
            } finally { passphrase.fill('\u0000') }
            if (!placeGenerationIsCurrent(generation)) return@launch
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupMessage = "Encrypted backup completed. Keep your recovery passphrase safe; it cannot be reset. Older readable backups are not automatically deleted.",
                            backupMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            backupBusy = false,
                            backupMessage = error.userMessage("Cloud backup failed."),
                            backupMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun selectBackupVersion(id: String?) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(selectedBackupVersion = id) }
    }
    fun loadBackupHistory() {
        val generation = currentPlaceGeneration() ?: return
        if (_uiState.value.backupBusy || _uiState.value.dataBusy) return
        _uiState.update { it.copy(backupBusy = true) }
        viewModelScope.launch {
            if (!placeGenerationIsCurrent(generation)) return@launch
            val result = app.backupCoordinator.versions()
            if (!placeGenerationIsCurrent(generation)) return@launch
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = { versions -> _uiState.update { it.copy(backupVersions = versions, backupBusy = false,
                    backupMessage = if (versions.isEmpty()) "No archive versions yet. Restore can still read an older encrypted backup." else "Choose a completed backup to restore.", backupMessageIsError = false) } },
                onFailure = { error -> _uiState.update { it.copy(backupBusy = false, backupMessage = error.userMessage("Could not load backup history."), backupMessageIsError = true) } })
        }
    }

    fun requestBackupRestore() {
        if (currentPlaceGeneration() == null) return
        _uiState.update { it.copy(backupRestoreConfirmation = true, backupMessage = null) }
    }

    fun cancelBackupRestore() {
        _uiState.update { it.copy(backupRestoreConfirmation = false) }
    }

    fun confirmBackupRestore() {
        val generation = backupDraftGeneration ?: currentPlaceGeneration() ?: return
        if (currentPlaceGeneration() != generation) return
        if (_uiState.value.backupBusy || _uiState.value.dataBusy) return
        if (!_uiState.value.legacyBackupRestore && _uiState.value.recoveryPassphrase.length < 20) {
            _uiState.update { it.copy(backupMessage = "Enter the recovery passphrase used for this backup (at least 20 characters).", backupMessageIsError = true) }
            return
        }
        val passphrase = _uiState.value.recoveryPassphrase.toCharArray()
        val legacy = _uiState.value.legacyBackupRestore
        val versionId = _uiState.value.selectedBackupVersion
        _uiState.update { it.copy(recoveryPassphrase = "", recoveryConfirmation = "") }
        _uiState.update {
            it.copy(backupBusy = true, backupRestoreConfirmation = false, backupMessage = null)
        }
        viewModelScope.launch {
            if (!placeGenerationIsCurrent(generation)) {
                passphrase.fill('\u0000')
                return@launch
            }
            val result = runCatching { if (legacy) {
                passphrase.fill('\u0000')
                app.backupCoordinator.restoreLegacyNow()
            } else app.backupCoordinator.restoreNow(passphrase, versionId) }
                .getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = {
                    // This restore advances the epoch once. A later erase/restore must own
                    // its own status; never reactivate capture for an obsolete completion.
                    if (CaptureStorageGate.dataGeneration.get() != generation + 1L) return@launch
                    currentPlaceGeneration()
                    val activationError = runCatching {
                        CaptureController.applyFromSettings(app)
                        if (app.settingsRepository.get().autoMiddayPulse) {
                            PulseScheduler.scheduleNext(app)
                        } else {
                            PulseScheduler.cancel(app)
                        }
                        HistoryRetentionWorker.applySchedule(
                            app,
                            app.settingsRepository.get().historyRetentionDays,
                        )
                    }.exceptionOrNull()
                    if (activationError != null) {
                        _uiState.update {
                            it.copy(
                                backupBusy = false,
                                backupMessage = activationError.userMessage(
                                    "Backup restored, but capture could not be restarted.",
                                ),
                                backupMessageIsError = true,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                backupBusy = false,
                                backupMessage = "Cloud backup restored on this phone.",
                                backupMessageIsError = false,
                            )
                        }
                    }
                    refresh()
                },
                onFailure = { error ->
                    showBackupRestoreFailure(error, generation)
                },
            )
        }
    }

    internal fun showBackupRestoreFailure(error: Throwable, startingGeneration: Long) {
        if (error is CancellationException) throw error
        val partial = error as? RestoreSettingsException
        if (partial != null) {
            // This restore, not a newer erase/restore, must still own the committed records.
            if (partial.committedDataGeneration != startingGeneration + 1L ||
                partial.committedDataGeneration != CaptureStorageGate.dataGeneration.get()) return
            currentPlaceGeneration()
        } else if (!placeGenerationIsCurrent(startingGeneration)) return
        _uiState.update { it.copy(backupBusy = false,
            backupMessage = error.userMessage("Cloud restore failed."), backupMessageIsError = true) }
        if (partial != null) refresh()
    }

    fun signOutOfBackup() {
        _uiState.update { it.copy(recoveryPassphrase = "", recoveryConfirmation = "") }
        if (_uiState.value.backupBusy || _uiState.value.dataBusy) return
        app.backupCoordinator.signOut()
        _uiState.update {
            it.copy(
                backupSignedInEmail = null,
                backupVersions = emptyList(),
                selectedBackupVersion = null,
                backupPasswordDraft = "",
                backupMessage = "Signed out. Local DailyBeat data remains on this phone.",
                backupMessageIsError = false,
                backupRestoreConfirmation = false,
            )
        }
    }

    fun requestCloudDataDeletion() {
        if (!_uiState.value.dataBusy) {
            _uiState.update { it.copy(cloudDeleteConfirmation = true, dataMessage = null) }
        }
    }

    fun cancelCloudDataDeletion() {
        _uiState.update { it.copy(cloudDeleteConfirmation = false) }
    }

    fun confirmCloudDataDeletion() {
        if (_uiState.value.dataBusy || _uiState.value.backupBusy) return
        _uiState.update {
            it.copy(dataBusy = true, cloudDeleteConfirmation = false, dataMessage = null)
        }
        viewModelScope.launch {
            app.backupCoordinator.deleteCloudData().fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            dataMessage = "Encrypted and legacy cloud backups were deleted. Your account and phone data remain.",
                            dataMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            dataMessage = error.userMessage("Unable to delete cloud backups."),
                            dataMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun requestAccountDeletion() {
        if (!_uiState.value.dataBusy) {
            _uiState.update {
                it.copy(accountDeleteConfirmation = true, accountDeletePassword = "", dataMessage = null)
            }
        }
    }

    fun setAccountDeletePassword(password: String) {
        backupDraftGeneration = currentPlaceGeneration() ?: return
        _uiState.update {
            it.copy(
                accountDeletePassword = InputPolicy.bounded(password, InputPolicy.BACKUP_PASSWORD_CHARS),
                dataMessage = null,
            )
        }
    }

    fun cancelAccountDeletion() {
        _uiState.update { it.copy(accountDeleteConfirmation = false, accountDeletePassword = "") }
    }

    fun confirmAccountDeletion() {
        val generation = backupDraftGeneration ?: return
        if (currentPlaceGeneration() != generation) return
        val state = _uiState.value
        val email = state.backupSignedInEmail ?: return
        if (state.dataBusy || state.backupBusy || state.accountDeletePassword.isBlank()) return
        val password = state.accountDeletePassword
        _uiState.update {
            it.copy(
                dataBusy = true,
                accountDeleteConfirmation = false,
                accountDeletePassword = "",
                dataMessage = null,
            )
        }
        viewModelScope.launch {
            if (!placeGenerationIsCurrent(generation)) return@launch
            val result = app.backupCoordinator.signIn(email, password).fold(
                onSuccess = {
                    if (placeGenerationIsCurrent(generation)) app.backupCoordinator.deleteAccount()
                    else Result.failure(IllegalStateException("Local data changed. Review this action again."))
                },
                onFailure = { Result.failure(it) },
            )
            if (!placeGenerationIsCurrent(generation)) return@launch
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            backupSignedInEmail = null,
                backupVersions = emptyList(),
                selectedBackupVersion = null,
                            backupPasswordDraft = "",
                            dataMessage = "Cloud account and all cloud backups were deleted. Data on this phone remains.",
                            dataMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            dataMessage = error.userMessage("Unable to delete the cloud account."),
                            dataMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun requestRetentionChange(days: Int) {
        if (days == _uiState.value.historyRetentionDays || _uiState.value.dataBusy) return
        require(days in com.dailybeat.app.data.settings.SettingsRepository.SUPPORTED_RETENTION_DAYS)
        if (days == 0) {
            app.settingsRepository.setHistoryRetentionDays(0)
            HistoryRetentionWorker.applySchedule(app, 0)
            _uiState.update {
                it.copy(
                    historyRetentionDays = 0,
                    pendingRetentionDays = null,
                    dataMessage = "History will be kept until you delete it.",
                    dataMessageIsError = false,
                )
            }
        } else {
            _uiState.update { it.copy(pendingRetentionDays = days, dataMessage = null) }
        }
    }

    fun cancelRetentionChange() {
        _uiState.update { it.copy(pendingRetentionDays = null) }
    }

    fun confirmRetentionChange() {
        val days = _uiState.value.pendingRetentionDays ?: return
        if (_uiState.value.dataBusy || _uiState.value.backupBusy) return
        val previousDays = _uiState.value.historyRetentionDays
        _uiState.update { it.copy(dataBusy = true, pendingRetentionDays = null, dataMessage = null) }
        viewModelScope.launch {
            runCatching {
                app.settingsRepository.setHistoryRetentionDays(days)
                HistoryRetentionWorker.applySchedule(app, days)
                app.historyRetentionManager.prune(days)
            }.fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            historyRetentionDays = days,
                            dataMessage = "Keeping $days days of history. ${result.recordsDeleted} older records were deleted.",
                            dataMessageIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    runCatching {
                        app.settingsRepository.setHistoryRetentionDays(previousDays)
                        HistoryRetentionWorker.applySchedule(app, previousDays)
                    }
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            dataMessage = error.userMessage("Unable to apply history retention."),
                            dataMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun requestLocalDataErase() {
        if (!_uiState.value.dataBusy) {
            _uiState.update { it.copy(localEraseConfirmation = true, dataMessage = null) }
        }
    }

    fun cancelLocalDataErase() {
        _uiState.update { it.copy(localEraseConfirmation = false) }
    }

    fun confirmLocalDataErase() {
        if (_uiState.value.dataBusy || _uiState.value.backupBusy) return
        _uiState.update { it.copy(dataBusy = true, localEraseConfirmation = false, dataMessage = null) }
        viewModelScope.launch {
            runCatching { app.localDataEraser.erase() }.fold(
                onSuccess = {
                    _uiState.update { SettingsUiState(localDataErased = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            dataBusy = false,
                            dataMessage = error.userMessage("Unable to erase all data on this phone."),
                            dataMessageIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun setSupervisorName(name: String) {
        val bounded = InputPolicy.singleLine(name, InputPolicy.PERSON_NAME_CHARS)
        app.settingsRepository.setSupervisorName(bounded)
        _uiState.update { it.copy(supervisorName = bounded) }
    }

    fun setThemePreference(preference: ThemePreference) {
        app.settingsRepository.setThemePreference(preference)
        _uiState.update { it.copy(themePreference = preference) }
    }

    fun addSuggestedPlace(suggestion: PlaceSuggestion) {
        if (_uiState.value.placeBusy) return
        val generation = currentPlaceGeneration() ?: return
        if (suggestion !in _uiState.value.placeSuggestions) {
            _uiState.update { it.copy(placeError = "This suggestion changed. Refresh Settings and try again.") }
            return
        }
        refreshSequence++
        _uiState.update { it.copy(placeBusy = true, placeError = null) }
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) {
                    app.db.withTransaction {
                        val current = FrequentPlaceLearner.suggest(app.visitRepository.visitsLastDays(14),
                            app.placeRepository.all())
                        check(suggestion in current) { "This suggestion changed. Refresh Settings and try again." }
                        app.placeRepository.add(suggestion.name, suggestion.latitude, suggestion.longitude)
                    }
                }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update { it.copy(placeBusy = false) }
                    refresh()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update {
                        it.copy(
                            placeBusy = false,
                            placeError = error.userMessage("Unable to save the suggested place."),
                        )
                    }
                },
            )
        }
    }

    fun loadAuditLog() {
        val generation = CaptureStorageGate.dataGeneration.get()
        viewModelScope.launch {
            val lines = CaptureAuditLog.readRecent(app)
            if (generation != CaptureStorageGate.dataGeneration.get()) return@launch
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
                            syntheticResult = error.userMessage("Unable to load synthetic data."),
                        )
                    }
                },
            )
        }
    }

    fun setOfficerName(name: String) {
        val bounded = InputPolicy.singleLine(name, InputPolicy.PERSON_NAME_CHARS)
        app.settingsRepository.setOfficerName(bounded)
        _uiState.update { it.copy(officerName = bounded) }
    }

    fun setGpsEnabled(enabled: Boolean) {
        app.settingsRepository.setGpsEnabled(enabled)
        _uiState.update {
            it.copy(gpsEnabled = enabled, captureMessage = captureStatusMessage(enabled))
        }
        CaptureController.applyFromSettings(app)
    }

    fun pauseCaptureForOneHour() {
        val resumeAt = System.currentTimeMillis() + 60 * 60_000L
        app.settingsRepository.pauseCaptureUntil(resumeAt)
        CaptureController.applyFromSettings(app)
        CaptureResumeWorker.schedule(app, resumeAt)
        _uiState.update { it.copy(capturePausedUntilMs = resumeAt) }
    }

    fun resumeCaptureNow() {
        app.settingsRepository.clearCapturePause()
        CaptureResumeWorker.cancel(app)
        CaptureController.applyFromSettings(app)
        _uiState.update { it.copy(capturePausedUntilMs = 0L) }
    }

    fun setCloudLlmEnabled(enabled: Boolean) {
        if (!enabled) {
            withdrawCloudConsent()
            return
        }
        val revision = ++cloudConsentRevision
        changeCloudSettings({
            if (revision == cloudConsentRevision) {
                app.settingsRepository.setCloudLlmEnabled(true)
                PulseScheduler.scheduleNext(app)
            }
        }) { if (revision == cloudConsentRevision) it.copy(cloudLlmEnabled = true) else it }
    }

    private fun withdrawCloudConsent() {
        cloudConsentRevision++
        cloudConnectionJob?.cancel()
        cloudConnectionJob = null
        app.settingsRepository.setCloudLlmEnabled(false)
        PulseScheduler.cancel(app)
        ReportRetryWorker.cancelAutomatic(app)
        _uiState.update { it.copy(cloudLlmEnabled = false, cloudTesting = false, cloudTestResult = null) }
    }

    fun setCloudProvider(providerId: String) {
        val provider = CloudProvider.entries.find { it.id == providerId } ?: CloudProvider.DEEPSEEK
        changeCloudSettings({
            app.settingsRepository.setCloudProvider(providerId)
            app.settingsRepository.setCloudModel(provider.defaultModel)
        }) {
            it.copy(cloudProvider = providerId, cloudModel = provider.defaultModel)
        }
    }

    fun setCloudModel(model: String) {
        val bounded = InputPolicy.singleLine(model, InputPolicy.CLOUD_MODEL_CHARS)
        changeCloudSettings({ app.settingsRepository.setCloudModel(bounded) }) {
            it.copy(cloudModel = bounded, cloudTestResult = null)
        }
    }

    fun setCloudBaseUrl(url: String) {
        val bounded = InputPolicy.singleLine(url, InputPolicy.CLOUD_URL_CHARS)
        changeCloudSettings({ app.settingsRepository.setCloudBaseUrl(bounded) }) {
            it.copy(cloudBaseUrl = bounded, cloudTestResult = null)
        }
    }

    fun setApiKeyDraft(key: String) {
        apiKeyDraftGeneration = currentPlaceGeneration() ?: return
        apiKeyDraftRevision++
        _uiState.update {
            it.copy(
                apiKeyDraft = InputPolicy.singleLine(key, InputPolicy.API_KEY_CHARS),
                cloudTestResult = null,
            )
        }
    }

    fun saveApiKey() {
        val generation = apiKeyDraftGeneration ?: return
        if (currentPlaceGeneration() != generation) return
        val key = _uiState.value.apiKeyDraft.trim()
        val draftRevision = apiKeyDraftRevision
        if (key.isEmpty() || _uiState.value.apiKeyBusy) return
        refreshSequence++
        _uiState.update { it.copy(apiKeyBusy = true, cloudTestResult = null) }
        launchCredentialJob(generation) {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) {
                    withContext(Dispatchers.IO) { secureApiKey.setApiKey(key) }
                }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                    PulseScheduler.scheduleNext(app)
                    _uiState.update {
                        it.copy(
                            apiKeyDraft = if (draftRevision == apiKeyDraftRevision) "" else it.apiKeyDraft,
                            hasApiKey = true,
                            apiKeyBusy = false,
                            cloudTestResult = "API key saved securely.",
                            cloudTestIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                    _uiState.update {
                        it.copy(
                            apiKeyBusy = false,
                            cloudTestResult = error.userMessage("Unable to save the API key securely."),
                            cloudTestIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun requestApiKeyRemoval() {
        val generation = currentPlaceGeneration() ?: return
        if (_uiState.value.hasApiKey && !_uiState.value.apiKeyBusy) {
            apiKeyRemovalGeneration = generation
            _uiState.update { it.copy(apiKeyRemovalConfirmation = true, cloudTestResult = null) }
        }
    }

    fun cancelApiKeyRemoval() {
        _uiState.update { it.copy(apiKeyRemovalConfirmation = false) }
    }

    fun confirmApiKeyRemoval() {
        val generation = apiKeyRemovalGeneration ?: return
        if (currentPlaceGeneration() != generation || !_uiState.value.apiKeyRemovalConfirmation) return
        if (_uiState.value.apiKeyBusy || !_uiState.value.hasApiKey) return
        val draftRevision = apiKeyDraftRevision
        // Stop using the key now; secure-store deletion may wait behind an active capture write.
        withdrawCloudConsent()
        refreshSequence++
        _uiState.update {
            it.copy(apiKeyBusy = true, apiKeyRemovalConfirmation = false, cloudTestResult = null)
        }
        launchCredentialJob(generation) {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) {
                    withContext(Dispatchers.IO) { secureApiKey.clearApiKey() }
                }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                    PulseScheduler.cancel(app)
                    _uiState.update {
                        it.copy(
                            apiKeyDraft = if (draftRevision == apiKeyDraftRevision) "" else it.apiKeyDraft,
                            hasApiKey = false,
                            apiKeyBusy = false,
                            cloudTestResult = "API key removed from this phone. Cloud requests are stopped.",
                            cloudTestIsError = false,
                        )
                    }
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                    _uiState.update {
                        it.copy(
                            apiKeyBusy = false,
                            cloudTestResult = error.userMessage("Unable to remove the API key securely."),
                            cloudTestIsError = true,
                        )
                    }
                },
            )
        }
    }

    fun setAutoEveningReport(enabled: Boolean) {
        val revision = ++eveningConsentRevision
        if (!enabled) {
            app.settingsRepository.setAutoEveningReport(false)
            ReportRetryWorker.cancelAutomatic(app)
            _uiState.update { it.copy(autoEveningReport = false) }
            return
        }
        changeCloudSettings({
            if (revision == eveningConsentRevision) app.settingsRepository.setAutoEveningReport(true)
        }) {
            if (revision == eveningConsentRevision) it.copy(autoEveningReport = true) else it
        }
    }

    fun testCloudConnection() {
        val generation = currentPlaceGeneration() ?: return
        if (_uiState.value.cloudTesting || _uiState.value.apiKeyBusy) return
        val draft = _uiState.value.apiKeyDraft.trim()
        val draftRevision = apiKeyDraftRevision
        if (draft.isNotEmpty() && apiKeyDraftGeneration != generation) return
        val settings = app.settingsRepository.get()
        _uiState.update { it.copy(cloudTesting = true, cloudTestResult = null) }
        cloudConnectionJob = launchCredentialJob(generation) {
            try {
                if (draft.isNotEmpty()) {
                    CaptureStorageGate.writeIfCurrent(generation) {
                        withContext(Dispatchers.IO) { secureApiKey.setApiKey(draft) }
                    }
                    if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                    PulseScheduler.scheduleNext(app)
                }
                if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                val result = cloudConnection.generate(
                    settings,
                    "You are a connection test. Return only the requested confirmation text.",
                    "Reply with exactly: DailyBeat cloud AI is connected.",
                    maxOutputTokens = CloudTokenBudgets.CONNECTION,
                )
                val hasApiKey = withContext(Dispatchers.IO) {
                    secureApiKey.hasApiKey()
                }
                if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                val testError = result.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        cloudTesting = false,
                        cloudTestResult = testError?.userMessage("Connection failed.")
                            ?: "Connected successfully.",
                        cloudTestIsError = testError != null,
                        hasApiKey = hasApiKey,
                        apiKeyDraft = if (draftRevision == apiKeyDraftRevision) "" else it.apiKeyDraft,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!placeGenerationIsCurrent(generation)) return@launchCredentialJob
                _uiState.update {
                    it.copy(
                        cloudTesting = false,
                        cloudTestResult = error.userMessage("Connection failed."),
                        cloudTestIsError = true,
                    )
                }
            }
        }
    }

    /** Fill the new-place coordinates from one fresh GPS fix, so the officer never types them. */
    fun captureCurrentLocationForPlace() {
        if (_uiState.value.placeLocating || _uiState.value.placeBusy) return
        val generation = currentPlaceGeneration() ?: return
        _uiState.update { it.copy(placeLocating = true, placeError = null) }
        viewModelScope.launch {
            val location = fetchCurrentLocation(app)
            if (!placeGenerationIsCurrent(generation)) return@launch
            _uiState.update {
                if (location == null) {
                    it.copy(
                        placeLocating = false,
                        placeError = "Couldn't get your location. Turn location on and try again outdoors.",
                    )
                } else {
                    it.copy(
                        placeLocating = false,
                        placeLat = location.latitude.toString(),
                        placeLon = location.longitude.toString(),
                        placeError = null,
                    )
                }
            }
        }
    }

    fun updatePlaceDraft(name: String, lat: String, lon: String) {
        if (currentPlaceGeneration() == null) return
        _uiState.update {
            it.copy(
                placeName = InputPolicy.singleLine(name, InputPolicy.PLACE_NAME_CHARS),
                placeLat = lat,
                placeLon = lon,
                placeError = null,
            )
        }
    }

    fun addPlace() {
        if (_uiState.value.placeBusy) return
        val generation = currentPlaceGeneration() ?: return
        val state = _uiState.value
        val name = state.placeName.trim()
        if (state.placeLat.isBlank() || state.placeLon.isBlank()) {
            _uiState.update { it.copy(placeError = "Use current location to set the spot.") }
            return
        }
        val validationError = PlaceInputValidator.errorFor(name, state.placeLat, state.placeLon)
        if (validationError != null) {
            _uiState.update { it.copy(placeError = validationError) }
            return
        }
        val lat = state.placeLat.toDouble()
        val lon = state.placeLon.toDouble()

        refreshSequence++
        _uiState.update { it.copy(placeBusy = true, placeError = null) }
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) { app.placeRepository.add(name, lat, lon) }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update {
                        it.copy(
                            placeName = "",
                            placeLat = "",
                            placeLon = "",
                            placeLocating = false,
                            placeBusy = false,
                            placeError = null,
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update {
                        it.copy(
                            placeBusy = false,
                            placeError = error.userMessage("Unable to add this place."),
                        )
                    }
                },
            )
        }
    }

    fun deletePlace(place: Place) {
        if (_uiState.value.placeBusy) return
        val generation = currentPlaceGeneration() ?: return
        if (place !in _uiState.value.places) {
            _uiState.update { it.copy(placeError = "This saved place changed. Refresh Settings and try again.") }
            return
        }
        refreshSequence++
        _uiState.update { it.copy(placeBusy = true, placeError = null) }
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) { app.placeRepository.delete(place) }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    // Reflect the completed delete immediately. A full refresh still follows for
                    // suggestions and other derived state, but the stale row must not remain
                    // tappable while that second database read is waiting to run.
                    _uiState.update { state ->
                        state.copy(
                            places = state.places.filterNot { candidate -> candidate.id == place.id },
                            placeBusy = false,
                            placeError = null,
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update {
                        it.copy(
                            placeBusy = false,
                            placeError = error.userMessage("Unable to delete this place."),
                        )
                    }
                },
            )
        }
    }

    fun setPlacePrivate(place: Place, isPrivate: Boolean) {
        if (_uiState.value.placeBusy) return
        val generation = currentPlaceGeneration() ?: return
        if (place !in _uiState.value.places) {
            _uiState.update { it.copy(placeError = "This saved place changed. Refresh Settings and try again.") }
            return
        }
        refreshSequence++
        _uiState.update { it.copy(placeBusy = true, placeError = null) }
        viewModelScope.launch {
            runCatching {
                CaptureStorageGate.writeIfCurrent(generation) { app.placeRepository.setPrivate(place, isPrivate) }
            }.fold(
                onSuccess = {
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update { state ->
                        state.copy(
                            places = state.places.map { candidate ->
                                if (candidate.id == place.id) candidate.copy(isPrivate = isPrivate) else candidate
                            },
                            placeBusy = false,
                            placeError = null,
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    if (!placeGenerationIsCurrent(generation)) return@launch
                    _uiState.update {
                        it.copy(
                            placeBusy = false,
                            placeError = error.userMessage("Unable to update privacy."),
                        )
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
        com.dailybeat.app.BuildConfig.GOOGLE_LOCATION && !PermissionHelper.hasActivityRecognition(app) ->
            "Allow Physical activity for battery-adaptive capture. DailyBeat will keep baseline tracking until then."
        !PermissionHelper.hasNotifications(app) ->
            "Notifications are off. Enable them so Android can show capture status."
        else -> null
    }

}
