package com.dailybeat.app.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import java.util.Collections

class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var visitTracker: VisitTracker
    private lateinit var app: DailyBeatApp
    @Volatile
    private var previousAccepted: LocationSample? = null
    @Volatile
    private var previousPersisted: LocationSample? = null
    @Volatile
    private var lastMeaningfulMovementMs: Long = 0L
    @Volatile
    private var activeProfile: ActiveCaptureProfile = ActiveCaptureProfile.MOVING
    private val breadcrumbWrites = Collections.synchronizedSet(mutableSetOf<Job>())

    private lateinit var backend: LocationBackend

    private fun onLocations(locations: List<android.location.Location>) {
            if (!::app.isInitialized || !app.settingsRepository.get().gpsCaptureEnabled ||
                app.settingsRepository.isCapturePaused()) return
            // Fused Location may batch several fixes to save battery. Feeding only lastLocation
            // drops the intermediate path and can turn a real stay into a single point.
            val breadcrumbs = mutableListOf<LocationBreadcrumb>()
            locations
                .sortedBy { it.time }
                .forEach { location ->
                    val sample = LocationSample(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        accuracyM = location.accuracy,
                        isMock = location.isMockLocation(),
                    )
                    app.captureHealthStore.fixObserved(sample.timestampMs)
                    val decision = LocationQualityFilter.assess(sample, previousAccepted)
                    if (!decision.accepted) {
                        app.captureHealthStore.rejected(sample.timestampMs, decision.reason ?: "unknown")
                        return@forEach
                    }
                    val priorAccepted = previousAccepted
                    previousAccepted = sample
                    app.captureHealthStore.accepted(sample, decision.quality)
                    if (priorAccepted == null) {
                        lastMeaningfulMovementMs = sample.timestampMs
                    } else if (
                        RoutePointSampler.distanceM(sample, priorAccepted) >=
                        AdaptiveCapturePolicy.MEANINGFUL_MOVEMENT_M
                    ) {
                        lastMeaningfulMovementMs = sample.timestampMs
                    }
                    if (RoutePointSampler.shouldPersist(sample, previousPersisted)) {
                        previousPersisted = sample
                        breadcrumbs += LocationBreadcrumb(
                            timestampMs = sample.timestampMs,
                            latitude = sample.latitude,
                            longitude = sample.longitude,
                            accuracyM = sample.accuracyM,
                            quality = decision.quality,
                        )
                    }
                    visitTracker.onLocation(sample.latitude, sample.longitude, sample.timestampMs)
                }
            enqueueBreadcrumbWrite(breadcrumbs)
            if (
                AdaptiveCapturePolicy.shouldStopForLocationIdle(
                    lastMeaningfulMovementMs = lastMeaningfulMovementMs,
                    nowMs = System.currentTimeMillis(),
                    hasMotionWatcher = MotionStateStore(this@LocationService).watcherArmed,
                )
            ) {
                // A transition callback normally stops us sooner. This is a conservative fallback
                // for handsets which keep delivering location but never emit STILL.
                stopSelf()
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::app.isInitialized || !app.settingsRepository.get().gpsCaptureEnabled ||
            app.settingsRepository.isCapturePaused() || !PermissionHelper.hasLocation(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val profile = intent?.getStringExtra(EXTRA_PROFILE)
            ?.let { runCatching { ActiveCaptureProfile.valueOf(it) }.getOrNull() }
        if (profile != null && ::app.isInitialized && activeProfile != profile) {
            configureLocationUpdates(profile)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        app = application as DailyBeatApp
        if (!PermissionHelper.hasLocation(this) || !app.settingsRepository.get().gpsCaptureEnabled ||
            app.settingsRepository.isCapturePaused()) {
            stopSelf()
            return
        }

        backend = LocationBackend(this)
        app.captureHealthStore.serviceStarted()
        scope.launch {
            app.breadcrumbRepository.latest()?.let { latest ->
                val restored = LocationSample(
                    latitude = latest.latitude,
                    longitude = latest.longitude,
                    timestampMs = latest.timestampMs,
                    accuracyM = latest.accuracyM,
                )
                withContext(Dispatchers.Main.immediate) {
                    if (previousAccepted == null || restored.timestampMs > previousAccepted!!.timestampMs) {
                        previousAccepted = restored
                    }
                    if (previousPersisted == null || restored.timestampMs > previousPersisted!!.timestampMs) {
                        previousPersisted = restored
                    }
                }
            }
        }
        visitTracker = VisitTracker(
            scope = scope,
            placeRepository = app.placeRepository,
            osmGeocoder = app.osmGeocoder,
            onVisitRecorded = { visit ->
                app.visitRepository.insert(visit)
                CaptureAuditLog.log(
                    this,
                    "visit",
                    "Visit recorded (${visit.visitType})",
                )
                val summary = when (visit.visitType) {
                    "transit" -> "Transit: ${visit.address ?: "en route"}"
                    else -> "Stay at ${visit.placeName ?: visit.address ?: "location"}"
                }
                runCatching {
                    app.db.events().insert(
                        Event(
                            timestamp = visit.startMs,
                            type = "visit",
                            rawText = summary,
                            placeName = visit.placeName,
                            latitude = visit.latitude,
                            longitude = visit.longitude,
                        ),
                    )
                }.onFailure { error ->
                    OperationalFailureLog.record(
                        context = this,
                        category = "capture-event-index",
                        retryable = true,
                        message = "Visit saved but timeline indexing failed " +
                            "(${error.javaClass.simpleName}).",
                    )
                }
            },
            stateStore = SharedPreferencesVisitTrackerStateStore(this),
            onWriteFailure = { error ->
                OperationalFailureLog.record(
                    context = this,
                    category = "capture-persist",
                    retryable = true,
                    message = "Captured visit could not be saved (${error.javaClass.simpleName}).",
                )
            },
        )

        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (error: Exception) {
            OperationalFailureLog.record(
                context = this,
                category = "capture-foreground",
                retryable = false,
                message = "Location foreground service could not start (${error.javaClass.simpleName}).",
            )
            stopSelf()
            return
        }
        configureLocationUpdates(ActiveCaptureProfile.MOVING)
    }

    private fun configureLocationUpdates(profile: ActiveCaptureProfile) {
        activeProfile = profile
        try {
            backend.start(profile, ::onLocations, onReady = { _running.value = true }, onError = { error ->
                OperationalFailureLog.record(this, "capture-location-updates", true,
                    "Location updates unavailable (${error.javaClass.simpleName}).")
                stopSelf()
            })
        } catch (error: Exception) {
            OperationalFailureLog.record(this, "capture-location-updates", false,
                "Location updates could not start (${error.javaClass.simpleName}).")
            stopSelf()
        }
    }

    private fun enqueueBreadcrumbWrite(points: List<LocationBreadcrumb>) {
        if (points.isEmpty()) return
        val write = scope.launch {
            runCatching {
                app.breadcrumbRepository.insertAll(points)
            }.onFailure { error ->
                OperationalFailureLog.record(
                    context = this@LocationService,
                    category = "capture-breadcrumb",
                    retryable = true,
                    message = "Route points could not be saved (${error.javaClass.simpleName}).",
                )
            }
        }
        breadcrumbWrites += write
        write.invokeOnCompletion { breadcrumbWrites -= write }
    }

    override fun onDestroy() {
        _running.value = false
        if (::app.isInitialized) app.captureHealthStore.serviceStopped()
        runCatching {
            if (::backend.isInitialized) backend.stop()
        }
        if (::visitTracker.isInitialized) {
            visitTracker.flushPending()
            scope.launch {
                visitTracker.awaitPendingWrites()
                synchronized(breadcrumbWrites) { breadcrumbWrites.toList() }.joinAll()
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_title))
            .setContentText(getString(R.string.location_service_passive))
            .setSmallIcon(R.drawable.ic_stat_dailybeat)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "location_capture"
        const val NOTIFICATION_ID = 1002
        private const val ACTION_UPDATE_PROFILE = "com.dailybeat.app.capture.UPDATE_PROFILE"
        private const val EXTRA_PROFILE = "capture_profile"

        private val _running = MutableStateFlow(false)

        /**
         * Whether capture is genuinely collecting, as opposed to merely being enabled in
         * Settings. Observable so the status the officer sees changes the moment the service
         * starts or stops, instead of showing whatever was true when the screen was built.
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        val isRunning: Boolean get() = _running.value

        fun start(
            context: Context,
            profile: ActiveCaptureProfile = ActiveCaptureProfile.MOVING,
        ): Result<Unit> = runCatching {
            context.startForegroundService(
                Intent(context, LocationService::class.java)
                    .putExtra(EXTRA_PROFILE, profile.name),
            )
            Unit
        }

        /** Updates a service that is already foreground; it never creates a new background FGS. */
        fun updateProfile(context: Context, profile: ActiveCaptureProfile) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, LocationService::class.java)
                        .setAction(ACTION_UPDATE_PROFILE)
                        .putExtra(EXTRA_PROFILE, profile.name),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}

@Suppress("DEPRECATION")
private fun android.location.Location.isMockLocation(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) isMock
    else isFromMockProvider
