package com.dailybeat.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Schedules optional midday cloud pulse summary (1 PM local). */
object PulseScheduler {

    fun scheduleNext(context: Context) {
        val app = context.applicationContext as com.dailybeat.app.DailyBeatApp
        if (!app.settingsRepository.get().autoMiddayPulse) return
        if (!app.settingsRepository.isCloudBrainReady()) return

        val alarm = context.getSystemService(AlarmManager::class.java)
        val trigger = nextOnePmMillis()
        val pending = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, MiddayPulseReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    fun cancel(context: Context) {
        val pending = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, MiddayPulseReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        if (pending != null) {
            context.getSystemService(AlarmManager::class.java).cancel(pending)
            pending.cancel()
        }
    }

    private fun nextOnePmMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 13)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }
}
