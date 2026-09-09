package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.cloud.MiddayPulseWorker

class MiddayPulseReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()
        if (settings.autoMiddayPulse && app.settingsRepository.isCloudBrainReady()) {
            MiddayPulseWorker.enqueue(context)
        }
        PulseScheduler.scheduleNext(context)
    }
}
