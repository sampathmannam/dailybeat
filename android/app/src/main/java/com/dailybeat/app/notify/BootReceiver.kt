package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reschedule the 8 PM reminder after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        DailyReminderScheduler.createChannel(context)
        DailyReminderScheduler.scheduleNext(context)
    }
}
