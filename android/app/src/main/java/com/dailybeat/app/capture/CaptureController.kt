package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        runCatching {
            if (settings.gpsCaptureEnabled && PermissionHelper.canCaptureLocation(context)) {
                LocationService.start(context)
            } else {
                LocationService.stop(context)
            }
        }.onFailure { error ->
            OperationalFailureLog.record(context, "capture-gps", false, error.message.orEmpty())
        }

        runCatching {
            if (settings.callLogEnabled && PermissionHelper.hasCallLog(context)) {
                CallLogWorker.schedule(context)
            } else {
                CallLogWorker.cancel(context)
            }
        }.onFailure { error ->
            OperationalFailureLog.record(context, "capture-call-log", false, error.message.orEmpty())
        }
    }
}
