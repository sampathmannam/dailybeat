package com.dailybeat.app.capture

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R

/** WorkManager cannot safely start a location FGS. A tap supplies the required visible context. */
object CaptureResumeNotification {
    fun show(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 1003, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        context.getSystemService(NotificationManager::class.java).notify(1003,
            NotificationCompat.Builder(context, LocationService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_dailybeat).setContentTitle("Resume DailyBeat capture")
                .setContentText("Open DailyBeat to resume recording your journey.")
                .setContentIntent(open).setAutoCancel(true).build())
    }
    fun clear(context: Context) { context.getSystemService(NotificationManager::class.java).cancel(1003) }
}
