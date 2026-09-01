package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        if (
            settings.gpsCaptureEnabled &&
            settings.activePatrolMissionId != null &&
            PermissionHelper.canCaptureLocation(context)
        ) {
            LocationService.start(context)
        } else {
            LocationService.stop(context)
        }
    }
}
