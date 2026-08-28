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
import kotlinx.coroutines.launch

class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var visitTracker: VisitTracker

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            visitTracker.onLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                timestampMs = location.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            },
        )

        startForeground(NOTIFICATION_ID, buildNotification())
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 45_000L)
            .setMinUpdateIntervalMillis(45_000L)
            .setMinUpdateDistanceMeters(75f)
            .setMaxUpdateDelayMillis(120_000L)
            .build()
        LocationServices.getFusedLocationProviderClient(this)
            .requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun onDestroy() {
        if (::visitTracker.isInitialized) {
            visitTracker.flushPending()
        }
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
