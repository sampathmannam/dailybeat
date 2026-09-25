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
        val date = DateKeys.today()

        if (settings.autoEveningReport && app.settingsRepository.isCloudBrainReady()) {
            // Generating the report is a cloud round trip that can outlast the few seconds a
            // broadcast receiver is allowed to live, so hand it to WorkManager, which also waits
            // for connectivity and retries instead of losing the day's report.
            ReportRetryWorker.enqueue(context, date)
        }

        val open = PendingIntent.getActivity(
            context,
            0,
            reviewDayIntent(context, date),
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

/** A reminder describes an action, not a generation result that may still be queued or fail. */
internal const val REVIEW_DAY_ACTION = "com.dailybeat.app.action.REVIEW_DAY"
internal const val REVIEW_DAY_EXTRA = "review_date_key"

internal fun reviewDayIntent(context: Context, date: java.time.LocalDate): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(REVIEW_DAY_ACTION)
        .putExtra(REVIEW_DAY_EXTRA, date.toString())
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

internal fun reminderReviewDate(intent: Intent?): String? {
    if (intent?.action != REVIEW_DAY_ACTION) return null
    val value = runCatching { intent.getStringExtra(REVIEW_DAY_EXTRA) }.getOrNull() ?: return null
    // MainActivity is exported. Reject arbitrary routes, oversized input and LocalDate's
    // extreme signed years, which cannot safely become epoch-millisecond query bounds.
    if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return null
    return runCatching { java.time.LocalDate.parse(value).toString() }.getOrNull()
}
