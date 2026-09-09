package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.notify.PulseScheduler

/** Re-arm alarms after reboot, app replacement, or a wall-clock/time-zone change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        DailyReminderScheduler.createChannel(context)
        DailyReminderScheduler.scheduleNext(context)
        PulseScheduler.scheduleNext(context)
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CaptureController.applyFromSettings(context)
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
