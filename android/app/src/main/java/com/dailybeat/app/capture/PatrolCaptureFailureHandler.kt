package com.dailybeat.app.capture

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker

/** Ends an affected patrol rather than allowing the UI to claim evidence is still recording. */
object PatrolCaptureFailureHandler {
    @Synchronized
    fun fail(app: DailyBeatApp, message: String): Boolean {
        val settings = app.settingsRepository.get()
        if (settings.activePatrolMissionId == null) return false
        app.settingsRepository.setPatrolCaptureError(message)
        val stopped = app.patrolGridRepository.endPatrol(
            pendingCloseReason = PatrolEndReason.DEVICE_ISSUE.storageValue,
        )
        if (app.isPatrolGridConfigured && stopped.missionId != null && stopped.sessionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
        }
        // Notify visible UI only after tracking state and any pending close are coherent.
        PatrolCaptureStatus.report(message)
        return true
    }
}
