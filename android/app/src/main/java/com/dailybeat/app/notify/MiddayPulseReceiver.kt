package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailybeat.app.DailyBeatApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MiddayPulseReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()
        if (!settings.autoMiddayPulse || !app.settingsRepository.isCloudBrainReady()) {
            PulseScheduler.scheduleNext(context)
            return
        }

        val pending = goAsync()
        scope.launch {
            app.pulseGenerator.generateAndSavePulse()
            pending.finish()
        }
        PulseScheduler.scheduleNext(context)
    }
}
