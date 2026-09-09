package com.dailybeat.app.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.util.PermissionHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var visitTracker: VisitTracker

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // Fused Location may batch several fixes to save battery. Feeding only lastLocation
            // drops the intermediate path and can turn a real stay into a single point.
            result.locations
                .sortedBy { it.time }
                .forEach { location ->
                    visitTracker.onLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    )
                }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onCreate() {
        super.onCreate()
        if (!PermissionHelper.hasLocation(this)) {
            stopSelf()
            return
        }

        val app = application as DailyBeatApp
        visitTracker = VisitTracker(
            scope = scope,
            placeRepository = app.placeRepository,
            osmGeocoder = app.osmGeocoder,
            onVisitRecorded = { visit ->
                app.visitRepository.insert(visit)
                CaptureAuditLog.log(
                    this,
                    "visit",
                    "${visit.visitType}: ${visit.placeName ?: visit.address ?: "coords"}",
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
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 45_000L)
            .setMinUpdateIntervalMillis(45_000L)
            .setMinUpdateDistanceMeters(75f)
            .setMaxUpdateDelayMillis(120_000L)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener { _running.value = true }
                .addOnFailureListener { error ->
                    OperationalFailureLog.record(
                        context = this,
                        category = "capture-location-updates",
                        retryable = true,
                        message = "Location updates failed (${error.javaClass.simpleName}).",
                    )
                    stopSelf()
                }
        } catch (error: SecurityException) {
            OperationalFailureLog.record(
                context = this,
                category = "capture-permission",
                retryable = false,
                message = "Location permission was revoked while capture started.",
            )
            stopSelf()
        }
    }

    override fun onDestroy() {
        _running.value = false
        if (::visitTracker.isInitialized) {
            visitTracker.flushPending()
            scope.launch {
                visitTracker.awaitPendingWrites()
                scope.cancel()
            }
        } else {
            scope.cancel()
        }
        runCatching {
            LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback)
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
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "location_capture"
        const val NOTIFICATION_ID = 1002

        private val _running = MutableStateFlow(false)

        /**
         * Whether capture is genuinely collecting, as opposed to merely being enabled in
         * Settings. Observable so the status the officer sees changes the moment the service
         * starts or stops, instead of showing whatever was true when the screen was built.
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        val isRunning: Boolean get() = _running.value

        fun start(context: Context): Result<Unit> = runCatching {
            context.startForegroundService(Intent(context, LocationService::class.java))
            Unit
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
