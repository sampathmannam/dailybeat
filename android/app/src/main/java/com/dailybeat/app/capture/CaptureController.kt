package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.PatrolRetentionStartupState
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        if (app.patrolRetentionStartupState.value != PatrolRetentionStartupState.READY) {
            LocationService.stop(context)
            return
        }

        val trackingRequested = settings.gpsCaptureEnabled &&
            settings.activePatrolMissionId != null
        when {
            !trackingRequested -> LocationService.stop(context)
            settings.patrolRetentionEnforcementFailureAtMs != null ||
                settings.patrolRetentionDeletionIntentCount > 0 -> {
                PatrolEvidenceIncidentStatus.report(
                    "GPS capture is blocked until PatrolGrid completes the required secure evidence cleanup.",
                )
                LocationService.stop(context)
            }
            !PermissionHelper.canCaptureLocation(context) -> {
                PatrolCaptureFailureHandler.fail(app, LocationService.LOCATION_PERMISSION_ERROR)
                LocationService.stop(context)
            }
            !LocationService.start(context) -> {
                PatrolCaptureFailureHandler.fail(app, LocationService.CAPTURE_START_ERROR)
                LocationService.stop(context)
            }
        }
    }
}
