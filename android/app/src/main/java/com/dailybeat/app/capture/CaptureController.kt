package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        val shouldStart = runCatching {
            settings.gpsCaptureEnabled &&
                !app.settingsRepository.isCapturePaused() &&
                PermissionHelper.canCaptureLocation(context)
        }.getOrElse { error ->
            OperationalFailureLog.record(
                context = context,
                category = "capture-permission",
                retryable = false,
                message = "Unable to evaluate location permission (${error.javaClass.simpleName}).",
            )
            false
        }
        val operation = if (shouldStart) {
            LocationService.start(context)
        } else {
            runCatching {
                LocationService.stop(context)
                Unit
            }
        }
        operation.onFailure { error ->
            OperationalFailureLog.record(
                context = context,
                category = "capture-gps",
                retryable = false,
                message = if (shouldStart) {
                    "GPS capture start failed (${error.javaClass.simpleName})."
                } else {
                    "GPS capture stop failed (${error.javaClass.simpleName})."
                },
            )
        }
    }
}
