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
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.dailybeat.app.data.db.CaptureFix
import com.dailybeat.app.data.db.CaptureCheckpoint
import java.util.concurrent.atomic.AtomicBoolean

class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, error ->
        OperationalFailureLog.record(this, "capture-recovery", true,
            "Capture recovery deferred (${error.javaClass.simpleName}).")
        CaptureRecoveryWorker.schedule(this)
    })
    private lateinit var app: DailyBeatApp
    @Volatile
    private var previousAccepted: LocationSample? = null
    @Volatile
    private var lastMeaningfulMovementMs: Long = 0L
    @Volatile
    private var activeProfile: ActiveCaptureProfile = ActiveCaptureProfile.MOVING

    private lateinit var backend: LocationBackend

    private fun onLocations(locations: List<android.location.Location>) {
        val generation = CaptureStorageGate.generation.get()
        val fixes = locations.map { location ->
            CaptureFix(location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                location.latitude, location.longitude, location.accuracy, location.isMockLocation())
        }
        scope.launch {
            try {
                CaptureStorageGate.mutex.withLock {
                    if (generation != CaptureStorageGate.generation.get() || !app.settingsRepository.get().gpsCaptureEnabled || app.settingsRepository.isCapturePaused()) return@withLock
                    // Persist before changing sampler/tracker state. Failed work stays in this inbox.
                    app.db.captureJournal().enqueue(fixes)
                    app.captureProcessor.drain(onRejected = app.captureHealthStore::rejected) { sample, quality ->
                        app.captureHealthStore.fixObserved(sample.timestampMs)
                        app.captureHealthStore.accepted(sample, quality)
                        val prior = previousAccepted
                        if (prior == null || RoutePointSampler.distanceM(sample, prior) >= AdaptiveCapturePolicy.MEANINGFUL_MOVEMENT_M) {
                            lastMeaningfulMovementMs = sample.timestampMs
                        }
                        previousAccepted = sample
                    }
                }
                if (CaptureController.canSleep(this@LocationService) &&
                    CaptureController.hasConfirmedStay(this@LocationService) &&
                    AdaptiveCapturePolicy.shouldStopForLocationIdle(lastMeaningfulMovementMs,
                        System.currentTimeMillis(), hasMotionWatcher = true)) {
                    CaptureStorageGate.mutex.withLock { app.captureProcessor.suspendCapture() }
                    withContext(Dispatchers.Main) { stopSelf() }
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error
            } catch (error: Exception) {
                app.captureHealthStore.storageFailed()
                CaptureRecoveryWorker.schedule(this@LocationService)
                OperationalFailureLog.record(this@LocationService, "capture-persist", true,
                    "Capture is queued for retry (${error.javaClass.simpleName}).")
            }
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
            CaptureStorageGate.mutex.withLock {
                val journal = app.db.captureJournal()
                if (journal.checkpoint() == null) {
                    val legacy = SharedPreferencesVisitTrackerStateStore(this@LocationService)
                    val memory = BufferedCheckpoint()
                    legacy.load()?.let(memory::save)
                    journal.checkpoint(CaptureCheckpoint(payload = memory.encode()))
                    legacy.clearSynchronously()
                }
            }
            // Replays a batch accepted before an earlier process died, even before a new GPS fix.
            onLocations(emptyList())
        }

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

    override fun onDestroy() {
        _running.value = false
        if (::app.isInitialized) app.captureHealthStore.serviceStopped()
        runCatching { if (::backend.isInitialized) backend.stop() }
        val discard = discardPendingOnDestroy.getAndSet(false)
        if (::app.isInitialized && !discard) {
            scope.launch {
                try {
                    CaptureStorageGate.mutex.withLock {
                        if (!app.settingsRepository.get().gpsCaptureEnabled || app.settingsRepository.isCapturePaused()) {
                            app.captureProcessor.finish()
                        } else app.captureProcessor.suspendCapture()
                        // A battery sleep or unexpected teardown keeps the durable open stay.
                    }
                } catch (error: Exception) {
                    app.captureHealthStore.storageFailed()
                    CaptureRecoveryWorker.schedule(this@LocationService)
                    OperationalFailureLog.record(this@LocationService, "capture-stop", true,
                        "Pending capture retained for retry (${error.javaClass.simpleName}).")
                } finally { scope.cancel() }
            }
        } else scope.cancel()
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
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setNumber(0)
            .setOngoing(true)
            .build()
    }

    companion object {
        // A new channel ID applies the no-badge policy to existing installations too; Android
        // intentionally freezes most behaviour after a notification channel is first created.
        const val CHANNEL_ID = "location_capture_status_v2"
        const val NOTIFICATION_ID = 1002
        private const val ACTION_UPDATE_PROFILE = "com.dailybeat.app.capture.UPDATE_PROFILE"
        private const val EXTRA_PROFILE = "capture_profile"

        private val _running = MutableStateFlow(false)
        private val discardPendingOnDestroy = AtomicBoolean(false)

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

        /** Stop capture for a confirmed local-data erase without re-inserting the open visit. */
        fun stopAndDiscard(context: Context) {
            discardPendingOnDestroy.set(true)
            if (!context.stopService(Intent(context, LocationService::class.java))) {
                discardPendingOnDestroy.set(false)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun android.location.Location.isMockLocation(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) isMock
    else isFromMockProvider
