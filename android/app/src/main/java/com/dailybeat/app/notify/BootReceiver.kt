package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.PatrolRetentionStartupState
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.LocationService

/** Restore only an explicitly active patrol session after device reboot. */
class BootReceiver : BroadcastReceiver() {
    internal var isPatrolGridConfigured: (Context) -> Boolean = { context ->
        (context.applicationContext as? DailyBeatApp)?.isPatrolGridConfigured == true
    }
    internal var retentionStartupState: (Context) -> PatrolRetentionStartupState = { context ->
        (context.applicationContext as? DailyBeatApp)
            ?.patrolRetentionStartupState
            ?.value
            ?: PatrolRetentionStartupState.BLOCKED
    }
    internal var applyConfiguredCapture: (Context) -> Unit = CaptureController::applyFromSettings
    internal var stopCapture: (Context) -> Unit = { context -> LocationService.stop(context) }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        // The application startup check owns configured PatrolGrid resume. Starting here
        // would race cleanup and could render/capture retained evidence before enforcement.
        if (isPatrolGridConfigured(context) &&
            retentionStartupState(context) != PatrolRetentionStartupState.READY
        ) {
            stopCapture(context)
            return
        }
        applyConfiguredCapture(context)
    }
}
