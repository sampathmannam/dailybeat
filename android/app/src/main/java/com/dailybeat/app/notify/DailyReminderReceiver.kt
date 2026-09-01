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
import com.dailybeat.app.cloud.ReportRetryPolicy
import com.dailybeat.app.cloud.ReportRetryWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class DailyReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        if (settings.autoEveningReport && app.settingsRepository.isCloudBrainReady()) {
            val pending = goAsync()
            scope.launch {
                val date = LocalDate.now()
                app.reportGenerator.generateAndSaveForDate(date).onFailure { error ->
                    if (ReportRetryPolicy.shouldRetry(error)) {
                        ReportRetryWorker.enqueue(context, date)
                    }
                }
                pending.finish()
            }
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, DailyReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
