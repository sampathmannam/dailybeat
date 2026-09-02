package com.dailybeat.app.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.util.PermissionHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LocationService : Service() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val dutyWindowClosed = AtomicBoolean(false)
    private val captureFailed = AtomicBoolean(false)
    private var deadlineJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val app = application as DailyBeatApp
            val settings = app.settingsRepository.get()
            if (settings.activePatrolDeadlineMs?.let { it <= System.currentTimeMillis() } == true) {
                closeAtDutyWindow(app)
                return
            }
            val missionId = settings.activePatrolMissionId ?: return
            val sessionId = settings.activePatrolSessionId
            val timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            if (!location.hasAccuracy() || !isPatrolLocationUsable(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyM = location.accuracy,
                    timestampMs = timestampMs,
                    nowMs = System.currentTimeMillis(),
                )
            ) {
                return
            }
            scope.launch {
                try {
                    val encryptedPayload = app.patrolTrackCipher.encrypt(
                        missionId = missionId,
                        timestampMs = timestampMs,
                        coordinates = PatrolCoordinates(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyM = location.accuracy,
                        ),
                    )
                    app.db.patrolTracks().insert(
                        PatrolTrackPoint(
                            missionId = missionId,
                            timestampMs = timestampMs,
                            encryptedPayload = encryptedPayload,
                            sessionId = sessionId,
                            clientPointId = sessionId?.let { UUID.randomUUID().toString() },
                        ),
                    )
                    if (sessionId != null && app.isPatrolGridConfigured) {
                        PatrolTrackSyncWorker.enqueue(app)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    // Route evidence must never fall back to plaintext when secure storage fails.
                    failCapture(app, SECURE_CAPTURE_ERROR)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as DailyBeatApp
        val settings = app.settingsRepository.get()
        if (settings.activePatrolMissionId == null) {
            stopSelf()
            return
        }
        if (!PermissionHelper.hasLocation(this)) {
            failCapture(app, LOCATION_PERMISSION_ERROR)
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        val deadlineMs = app.settingsRepository.get().activePatrolDeadlineMs
        if (deadlineMs != null) {
            if (deadlineMs <= System.currentTimeMillis()) {
                closeAtDutyWindow(app)
                return
            }
            deadlineJob = scope.launch {
                delay((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L))
                closeAtDutyWindow(app)
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setMinUpdateDistanceMeters(25f)
            // A field map must receive the first useful fix promptly. Batching can leave
            // the screen looking stalled for a full minute even while GNSS is active.
            .setWaitForAccurateLocation(false)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission can be revoked after the guard at the start of onCreate().
            failCapture(app, LOCATION_PERMISSION_ERROR)
        }
    }

    override fun onDestroy() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback)
        deadlineJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun closeAtDutyWindow(app: DailyBeatApp) {
        if (!dutyWindowClosed.compareAndSet(false, true)) return
        val settings = app.settingsRepository.get()
        val stopped = app.patrolGridRepository.endPatrol(
            pendingCloseReason = PatrolEndReason.DUTY_WINDOW_ENDED.storageValue,
            endedAtMs = settings.activePatrolDeadlineMs ?: System.currentTimeMillis(),
        )
        if (app.isPatrolGridConfigured && stopped.missionId != null && stopped.sessionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
        }
        if (PermissionHelper.hasNotifications(this)) {
            try {
                NotificationManagerCompat.from(this).notify(
                    DUTY_WINDOW_NOTIFICATION_ID,
                    buildDutyWindowEndedNotification(),
                )
            } catch (_: SecurityException) {
                // Notification permission can be revoked after the explicit guard.
            }
        }
        stopSelf()
    }

    private fun failCapture(app: DailyBeatApp, message: String) {
        if (!captureFailed.compareAndSet(false, true)) return
        PatrolCaptureFailureHandler.fail(app, message)
        stopSelf()
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

    private fun buildDutyWindowEndedNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_service_duty_window_ended_title))
            .setContentText(getString(R.string.location_service_duty_window_ended_body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()

    companion object {
        const val CHANNEL_ID = "location_capture"
        const val NOTIFICATION_ID = 1002
        const val DUTY_WINDOW_NOTIFICATION_ID = 1003
        const val SECURE_CAPTURE_ERROR =
            "Secure GPS recording stopped because this device could not protect the route evidence."
        const val LOCATION_PERMISSION_ERROR =
            "GPS recording stopped because location permission is no longer available."
        const val CAPTURE_START_ERROR =
            "GPS recording could not start on this device. The patrol was ended to protect evidence integrity."

        fun start(context: Context): Boolean =
            try {
                context.startForegroundService(Intent(context, LocationService::class.java))
                true
            } catch (_: IllegalStateException) {
                // Foreground services unavailable in unit tests or restricted contexts.
                false
            } catch (_: SecurityException) {
                false
            }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}

internal fun isPatrolLocationUsable(
    latitude: Double,
    longitude: Double,
    accuracyM: Float,
    timestampMs: Long,
    nowMs: Long,
): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 &&
        accuracyM.isFinite() && accuracyM in 0f..MAX_ACCEPTED_ACCURACY_M &&
        timestampMs in (nowMs - MAX_LOCATION_AGE_MS)..(nowMs + MAX_LOCATION_CLOCK_SKEW_MS)

private const val MAX_ACCEPTED_ACCURACY_M = 5_000f
private const val MAX_LOCATION_AGE_MS = 5 * 60 * 1_000L
private const val MAX_LOCATION_CLOCK_SKEW_MS = 5 * 60 * 1_000L
