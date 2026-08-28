package com.dailybeat.app.capture

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R

class VoiceCaptureService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    startForeground(NOTIFICATION_ID, buildNotification())
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY
  }

  private fun buildNotification(): Notification {
    val openIntent = PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle(getString(R.string.voice_service_title))
      .setContentText(getString(R.string.voice_service_text))
      .setSmallIcon(android.R.drawable.ic_btn_speak_now)
      .setContentIntent(openIntent)
      .setOngoing(true)
      .build()
  }

  companion object {
    const val CHANNEL_ID = "voice_capture"
    const val NOTIFICATION_ID = 1001

    fun start(context: Context) {
      context.startForegroundService(Intent(context, VoiceCaptureService::class.java))
    }
  }
}
