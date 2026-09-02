package com.dailybeat.app.capture

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker

/** Ends an affected patrol rather than allowing the UI to claim evidence is still recording. */
object PatrolCaptureFailureHandler {
    @Synchronized
    fun fail(app: DailyBeatApp, message: String): Boolean {
        val settings = app.settingsRepository.get()
        val missionId = settings.activePatrolMissionId ?: return false
        val sessionId = settings.activePatrolSessionId

        app.settingsRepository.setPatrolCaptureError(message)
        app.patrolGridRepository.endPatrol()
        if (app.isPatrolGridConfigured && sessionId != null) {
            app.settingsRepository.setPendingPatrolClose(
                sessionId = sessionId,
                missionId = missionId,
                reason = PatrolEndReason.DEVICE_ISSUE.storageValue,
            )
            PatrolTrackSyncWorker.enqueue(app)
        }
        // Notify visible UI only after tracking state and any pending close are coherent.
        PatrolCaptureStatus.report(message)
        return true
    }
}
