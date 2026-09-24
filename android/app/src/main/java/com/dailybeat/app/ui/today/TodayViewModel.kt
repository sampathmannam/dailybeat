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
import com.dailybeat.app.capture.MotionStateStore
import com.dailybeat.app.capture.status
import com.dailybeat.app.capture.VoiceCaptureOrchestrator
import com.dailybeat.app.synthetic.SyntheticDayGenerator
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dailybeat.app.util.userMessage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayUiState(
    val visitCount: Int = 0,
    val eventCount: Int = 0,
    val hasDiary: Boolean = false,
    val cloudBrainReady: Boolean = false,
    val gpsEnabled: Boolean = true,
    val gpsActive: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val isGeneratingReport: Boolean = false,
    val isSeeding: Boolean = false,
    val seedMessage: String? = null,
    val isRecordingVoice: Boolean = false,
    val isSavingNote: Boolean = false,
    val isSavingMoment: Boolean = false,
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

/**
 * The local day is more than a date: changing the phone's time zone can change Room query
 * boundaries even when the calendar label stays the same. Keeping both values in the key makes
 * long-lived Today screens resubscribe after midnight and after a time-zone change.
 */
internal data class LocalDayContext(
    val date: LocalDate,
    val zoneId: ZoneId,
)

internal fun localDayContext(
    nowMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): LocalDayContext = LocalDayContext(
    date = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate(),
    zoneId = zoneId,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as DailyBeatApp
    private val repository = app.eventRepository
    private val diaryRepository = app.diaryRepository

    private val clockTicks = minuteTicker()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            System.currentTimeMillis(),
        )

    private val activeDay = clockTicks
        .map { localDayContext(it) }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            localDayContext(),
        )

    val todayVisits = activeDay.flatMapLatest { day ->
        app.visitRepository.observeForDate(day.date)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEvents = activeDay.flatMapLatest { day ->
        repository.observeEventsForDate(day.date)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A saved name may be added after a visit/event was captured. Observe it locally so
    // Today can label that existing moment immediately, without rewriting history or
    // sending old coordinates to an external lookup service.
    val savedPlaces = app.placeRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayBreadcrumbs = activeDay.flatMapLatest { day ->
        app.breadcrumbRepository.observeForDate(day.date)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayBeat = activeDay.flatMapLatest { day ->
        combine(
            app.visitRepository.observeForDate(day.date),
            app.breadcrumbRepository.observeForDate(day.date),
            app.beatRepository.observe(day.date),
            app.placeRepository.observeAll(),
            diaryRepository.observeForDate(day.date),
        ) { visits, breadcrumbs, review, places, diary ->
            DayFeedBuilder.build(
                date = day.date,
                visits = visits,
                diaryText = diary?.text,
                places = places,
                breadcrumbs = breadcrumbs,
                review = review,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayFeedBuilder.build(activeDay.value.date, emptyList(), null),
    )

    /**
     * A deliberately local analysis. It refreshes when a visit is completed, but never uploads
     * history or invokes a cloud model merely because Today is open.
     */
    internal val patternAnalysis = combine(activeDay, todayVisits) { day, visits -> day to visits }
        .mapLatest { (day, visits) ->
            withContext(Dispatchers.IO) {
                buildTodayPatternAnalysis(
                    today = day.date,
                    zoneId = day.zoneId,
                    todayVisits = visits,
                    recentVisits = app.visitRepository.visitsLastDays(28),
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TodayPatternAnalysis(),
        )

    private val _uiState = MutableStateFlow(
        TodayUiState(
            cloudBrainReady = runCatching {
                app.settingsRepository.isCloudBrainReady()
            }.getOrDefault(false),
            gpsEnabled = runCatching {
                app.settingsRepository.get().gpsCaptureEnabled
            }.getOrDefault(false),
            locationPermissionGranted = PermissionHelper.canCaptureLocation(application),
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
                        locationPermissionGranted = PermissionHelper.canCaptureLocation(getApplication()),
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
                clockTicks,
            ) { beat, health, running, now ->
                val enabled = runCatching { app.settingsRepository.get().gpsCaptureEnabled }
                    .getOrDefault(false)
                // The privacy pause is real state on disk, not a UI flag: read it every tick so
                // Today stops calling a chosen pause "Capture is off", and so the card flips back
                // to the live state by itself the minute the deadline passes.
                val pausedUntil = runCatching { app.settingsRepository.capturePausedUntilMs(now) }
                    .getOrDefault(0L)
                beat to health.copy(serviceRunning = running).status(
                    nowMs = now,
                    enabled = enabled,
                    pausedUntilMs = pausedUntil,
                    watcherArmed = MotionStateStore(getApplication()).watcherArmed &&
                        PermissionHelper.canStartLocationCaptureFromBackground(getApplication()),
                )
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

    fun markSignificantMoment(onSaved: () -> Unit = {}) {
        if (_uiState.value.isSavingMoment) return
        _uiState.update { it.copy(isSavingMoment = true, error = null) }
        viewModelScope.launch {
            runCatching {
                repository.addMomentMarker("Significant moment flagged (passive marker)")
                CaptureAuditLog.log(getApplication(), "moment", "User flagged significant moment")
            }.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSavingMoment = false,
                            error = null,
                            successMessage = "Moment saved at the current time.",
                        )
                    }
                    onSaved()
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isSavingMoment = false) }
                    showError(error, "Unable to mark this moment.")
                },
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
                onSuccess = {
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

    /**
     * Recomputes the capture card now rather than waiting for the next minute tick.
     *
     * The tick alone is too slow for the two moments that matter: tapping "Resume capture now"
     * here, and coming back from the Settings row that started the pause. Both would otherwise
     * leave Today insisting capture was still paused for up to a minute after it was not.
     */
    fun refreshStatus() {
        runCatching {
            val settings = app.settingsRepository.get()
            val now = System.currentTimeMillis()
            val status = app.captureHealthStore.health.value
                .copy(serviceRunning = LocationService.isRunning)
                .status(
                    nowMs = now,
                    enabled = settings.gpsCaptureEnabled,
                    pausedUntilMs = app.settingsRepository.capturePausedUntilMs(now),
                    watcherArmed = MotionStateStore(getApplication()).watcherArmed &&
                        PermissionHelper.canStartLocationCaptureFromBackground(getApplication()),
                )
            _uiState.update { current ->
                current.copy(
                    cloudBrainReady = app.settingsRepository.isCloudBrainReady(),
                    gpsEnabled = settings.gpsCaptureEnabled,
                    locationPermissionGranted = PermissionHelper.canCaptureLocation(getApplication()),
                    gpsActive = isGpsCaptureActive(),
                    captureStatus = status,
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
            val now = System.currentTimeMillis()
            emit(now)
            // Align to the next wall-clock minute. A screen opened at 23:59:58 therefore rolls
            // over at midnight, not nearly a minute later.
            val untilNextMinute = 60_000L - Math.floorMod(now, 60_000L)
            delay(untilNextMinute.coerceIn(1L, 60_000L))
        }
    }
}
