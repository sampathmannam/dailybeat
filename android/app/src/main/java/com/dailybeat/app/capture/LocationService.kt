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
  private var lastLat: Double? = null
  private var lastLon: Double? = null
  private var lastSavedMs: Long = 0L

  private val callback = object : LocationCallback() {
    override fun onLocationResult(result: LocationResult) {
      val location = result.lastLocation ?: return
      val now = System.currentTimeMillis()
      val moved = lastLat?.let { lat ->
        val dLat = location.latitude - lat
        val dLon = location.longitude - (lastLon ?: 0.0)
        Math.hypot(dLat, dLon) > 0.001
      } ?: true
      val elapsed = now - lastSavedMs
      if (!moved && elapsed < 5 * 60 * 1000) return

      lastLat = location.latitude
      lastLon = location.longitude
      lastSavedMs = now

      scope.launch {
        val app = application as DailyBeatApp
        app.db.events().insert(
          Event(
            timestamp = now,
            type = "gps",
            rawText = "GPS breadcrumb",
            latitude = location.latitude,
            longitude = location.longitude,
          ),
        )
      }
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    if (!PermissionHelper.hasLocation(this)) {
      stopSelf()
      return
    }
    startForeground(NOTIFICATION_ID, buildNotification())
    val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
      .setMinUpdateIntervalMillis(60_000L)
      .setMinUpdateDistanceMeters(100f)
      .build()
    LocationServices.getFusedLocationProviderClient(this)
      .requestLocationUpdates(request, callback, Looper.getMainLooper())
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
      .setContentText(getString(R.string.location_service_text))
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentIntent(openIntent)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val CHANNEL_ID = "location_capture"
    const val NOTIFICATION_ID = 1002

    fun start(context: Context) {
      context.startForegroundService(Intent(context, LocationService::class.java))
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, LocationService::class.java))
    }
  }
}
