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
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.util.PermissionHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val app = application as DailyBeatApp
            val missionId = app.settingsRepository.get().activePatrolMissionId ?: return
            val timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            scope.launch {
                val encryptedPayload = try {
                    app.patrolTrackCipher.encrypt(
                        missionId = missionId,
                        timestampMs = timestampMs,
                        coordinates = PatrolCoordinates(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyM = location.accuracy,
                        ),
                    )
                } catch (_: Exception) {
                    // Route evidence must never fall back to plaintext if the keystore is unavailable.
                    stopSelf()
                    return@launch
                }
                app.db.patrolTracks().insert(
                    PatrolTrackPoint(
                        missionId = missionId,
                        timestampMs = timestampMs,
                        encryptedPayload = encryptedPayload,
                    ),
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as DailyBeatApp
        if (!PermissionHelper.hasLocation(this) || app.settingsRepository.get().activePatrolMissionId == null) {
            stopSelf()
            return
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(20_000L)
            .setMinUpdateDistanceMeters(25f)
            .setMaxUpdateDelayMillis(60_000L)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            // Permission can be revoked after the guard at the start of onCreate().
            stopSelf()
        }
    }

    override fun onDestroy() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(callback)
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

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, LocationService::class.java))
            } catch (_: IllegalStateException) {
                // Foreground services unavailable in unit tests or restricted contexts.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationService::class.java))
        }
    }
}
