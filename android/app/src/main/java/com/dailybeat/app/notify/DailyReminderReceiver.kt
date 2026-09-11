package com.dailybeat.app.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R
import com.dailybeat.app.cloud.ReportRetryWorker
import com.dailybeat.app.util.DateKeys

class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        if (settings.autoEveningReport && app.settingsRepository.isCloudBrainReady()) {
            // Generating the report is a cloud round trip that can outlast the few seconds a
            // broadcast receiver is allowed to live, so hand it to WorkManager, which also waits
            // for connectivity and retries instead of losing the day's report.
            ReportRetryWorker.enqueue(context, DateKeys.today())
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, DailyReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_dailybeat)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_body_report))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(DailyReminderScheduler.NOTIFICATION_ID, notification)
        DailyReminderScheduler.scheduleNext(context)
    }
}
