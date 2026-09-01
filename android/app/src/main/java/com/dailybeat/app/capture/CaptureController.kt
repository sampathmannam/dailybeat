package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()
        val startGps = runCatching {
            settings.gpsCaptureEnabled && PermissionHelper.canCaptureLocation(context)
        }
        val scheduleCallLog = runCatching {
            settings.callLogEnabled && PermissionHelper.hasCallLog(context)
        }

        applyCaptureOperations(
            context = context,
            startGps = startGps.getOrDefault(settings.gpsCaptureEnabled),
            scheduleCallLog = scheduleCallLog.getOrDefault(settings.callLogEnabled),
            gpsOperation = {
                startGps.fold(
                    onSuccess = { shouldStart ->
                        if (shouldStart) LocationService.start(context) else runCatching {
                            LocationService.stop(context)
                            Unit
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
            },
            callLogOperation = {
                scheduleCallLog.fold(
                    onSuccess = { shouldSchedule ->
                        if (shouldSchedule) {
                            CallLogWorker.schedule(context)
                        } else {
                            CallLogWorker.cancel(context)
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
            },
        )
    }

    internal fun applyCaptureOperations(
        context: Context,
        startGps: Boolean,
        scheduleCallLog: Boolean,
        gpsOperation: () -> Result<Unit>,
        callLogOperation: () -> Result<Unit>,
    ) {
        runCatching { gpsOperation().getOrThrow() }.onFailure {
            OperationalFailureLog.record(
                context,
                "capture-gps",
                false,
                if (startGps) "GPS capture start failed." else "GPS capture stop failed.",
            )
        }
        runCatching { callLogOperation().getOrThrow() }.onFailure {
            OperationalFailureLog.record(
                context,
                "capture-call-log",
                false,
                if (scheduleCallLog) {
                    "Call-log scheduling failed."
                } else {
                    "Call-log cancellation failed."
                },
            )
        }
    }
}
