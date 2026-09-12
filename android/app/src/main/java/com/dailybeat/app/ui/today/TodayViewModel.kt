package com.dailybeat.app.ui.today

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.CaptureHealthStatus
import com.dailybeat.app.capture.CaptureResumeWorker
import com.dailybeat.app.capture.CaptureHealthLevel
import com.dailybeat.app.capture.status
import com.dailybeat.app.capture.VoiceCaptureOrchestrator
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.dailybeat.app.util.userMessage

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
    val isSavingNote: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val captureStatus: CaptureHealthStatus = CaptureHealthStatus(CaptureHealthLevel.OFF),
    val beatTitle: String = "Your day in motion",
    val beatState: String = "live",
    val distanceMeters: Double = 0.0,
    val trackedMinutes: Long = 0,
    val captureGapCount: Int = 0,
    val distanceEstimated: Boolean = true,
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

    val todayBreadcrumbs = app.breadcrumbRepository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayBeat = combine(
        todayVisits,
        todayBreadcrumbs,
        app.beatRepository.observe(DateKeys.today()),
        app.placeRepository.observeAll(),
        diaryRepository.observeToday(),
    ) { visits, breadcrumbs, review, places, diary ->
        DayFeedBuilder.build(
            date = DateKeys.today(),
            visits = visits,
            diaryText = diary?.text,
            places = places,
            breadcrumbs = breadcrumbs,
            review = review,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayFeedBuilder.build(DateKeys.today(), emptyList(), null),
    )

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
        viewModelScope.launch {
            combine(
                todayBeat,
                app.captureHealthStore.health,
                LocationService.running,
                minuteTicker(),
            ) { beat, health, running, now ->
                val enabled = runCatching { app.settingsRepository.get().gpsCaptureEnabled }
                    .getOrDefault(false)
                // The privacy pause is real state on disk, not a UI flag: read it every tick so
                // Today stops calling a chosen pause "Capture is off", and so the card flips back
                // to the live state by itself the minute the deadline passes.
                val pausedUntil = runCatching { app.settingsRepository.capturePausedUntilMs(now) }
                    .getOrDefault(0L)
                beat to health.copy(serviceRunning = running).status(now, enabled, pausedUntil)
            }.collect { (beat, status) ->
                _uiState.update {
                    it.copy(
                        captureStatus = status,
                        beatTitle = beat.title,
                        beatState = beat.state,
                        distanceMeters = beat.distanceMeters,
                        trackedMinutes = beat.trackedMinutes,
                        captureGapCount = beat.captureGapCount,
                        distanceEstimated = beat.distanceEstimated,
                    )
                }
            }
        }
    }

    fun addOptionalNote(text: String, onSaved: () -> Unit = {}) {
        if (_uiState.value.isSavingNote) return
        _uiState.update { it.copy(isSavingNote = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.addManualEvent(text) }.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSavingNote = false, error = null) }
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSavingNote = false) }
                    showError(error, "Unable to save the note.")
                },
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
                onSuccess = {
                    _uiState.update { it.copy(error = null, successMessage = "Moment saved at the current time.") }
                },
                onFailure = { error -> showError(error, "Unable to mark this moment.") },
            )
        }
    }

    fun recordVoiceNote() {
        if (_uiState.value.isRecordingVoice) return
        _uiState.value = _uiState.value.copy(isRecordingVoice = true, error = null)
        viewModelScope.launch {
            val result = runCatching { VoiceCaptureOrchestrator(app).captureAndSave() }
                .getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { transcript ->
                    _uiState.value = _uiState.value.copy(
                        isRecordingVoice = false,
                        successMessage = "Voice note saved.",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRecordingVoice = false,
                        error = error.userMessage("Voice capture failed."),
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
        _uiState.value = _uiState.value.copy(isSeeding = true, seedMessage = null, error = null)
        viewModelScope.launch {
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
        _uiState.value = _uiState.value.copy(isGeneratingReport = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                app.reportGenerator.generateAndSaveForDate(com.dailybeat.app.util.DateKeys.today())
            }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingReport = false,
                        successMessage = "Daily report generated and saved.",
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGeneratingReport = false,
                        error = error.userMessage("Report failed."),
                    )
                },
            )
        }
    }

    /**
     * Undoes the one-hour privacy pause from Today, so the officer does not have to go find the
     * Settings row that started it. Mirrors SettingsViewModel.resumeCaptureNow exactly: clear the
     * deadline, cancel the scheduled resume, restart capture.
     */
    fun resumeCaptureNow() {
        runCatching {
            app.settingsRepository.clearCapturePause()
            CaptureResumeWorker.cancel(getApplication())
            CaptureController.applyFromSettings(getApplication())
        }.onFailure { error -> showError(error, "Unable to resume capture.") }
        refreshStatus()
    }

    fun clearMessage() {
        _uiState.update { it.copy(successMessage = null) }
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
        _uiState.update { it.copy(error = error.userMessage(fallback)) }
    }

    private fun isGpsCaptureActive(): Boolean {
        val settings = app.settingsRepository.get()
        return settings.gpsCaptureEnabled &&
            PermissionHelper.canCaptureLocation(getApplication()) &&
            LocationService.isRunning
    }

    private fun minuteTicker() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }
}
