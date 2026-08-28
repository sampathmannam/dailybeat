package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        if (settings.gpsCaptureEnabled && PermissionHelper.hasLocation(context)) {
            LocationService.start(context)
        } else {
            LocationService.stop(context)
        }

        if (settings.callLogEnabled && PermissionHelper.hasCallLog(context)) {
            CallLogWorker.schedule(context)
        } else {
            CallLogWorker.cancel(context)
        }
    }
}
