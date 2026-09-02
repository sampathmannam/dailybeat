package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureController
import java.util.concurrent.TimeUnit

class PatrolTrackSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        // Unknown clocks may pass only through this authenticated network recovery
        // worker; capture and protected UI remain blocked until the final strict pass.
        val retention = app.patrolEvidenceRetentionManager.enforce(
            allowUnknownClockRecovery = true,
        )
        if (retention.isFailure) {
            CaptureController.applyFromSettings(app)
            return if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
        }
        val retentionIncident = retention.getOrThrow().hasIncident
        val syncResult = app.patrolActionOutbox.syncPending().fold(
            onSuccess = { app.patrolTrackSyncer.syncAndClosePendingSession() },
            onFailure = { kotlin.Result.failure(it) },
        )
        return syncResult.fold(
            onSuccess = {
                val currentSession = app.patrolGridRemote.currentSession()
                if (currentSession != null) {
                    val current = app.settingsRepository.get()
                    val refreshed = app.patrolGridRemote.loadSnapshot(
                        current.activePatrolMissionId ?: current.pendingPatrolCloseMissionId,
                    ).mapCatching { snapshot -> app.patrolGridSnapshotCache.save(snapshot) }
                    if (refreshed.isFailure) {
                        return@fold if (runAttemptCount >= MAX_RETRIES) {
                            Result.failure()
                        } else {
                            Result.retry()
                        }
                    }
                }
                val strictRetention = app.patrolEvidenceRetentionManager.enforce()
                if (strictRetention.isFailure) {
                    CaptureController.applyFromSettings(app)
                    return@fold if (runAttemptCount >= MAX_RETRIES) {
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                }
                val settings = app.settingsRepository.get()
                if (settings.activePatrolMissionId == null &&
                    settings.pendingPatrolCloseSessionId == null &&
                    app.patrolActionOutbox.pendingCount() == 0 &&
                    app.db.patrolTracks().accountOwnedEvidenceCount() == 0
                ) {
                    app.settingsRepository.setPatrolEvidenceOwner(null)
                }
                // The expiry is non-retryable: it is persisted, shown in the field UI,
                // and recorded in WorkInfo output without disabling the periodic safety
                // net or repeatedly retrying evidence that was correctly deleted.
                Result.success(
                    workDataOf(
                        KEY_RETENTION_INCIDENT to
                            (retentionIncident || strictRetention.getOrThrow().hasIncident),
                    ),
                )
            },
            onFailure = { error ->
                val unavailable = error as? PatrolEvidenceDestinationUnavailableException
                if (unavailable != null) {
                    val cleanup = app.patrolEvidenceRetentionManager
                        .discardUnavailableMission(unavailable.missionId)
                    if (cleanup.isSuccess) {
                        // A queue can contain more than one mission. Do not let cleanup of
                        // the first authoritative P0002 clear the capture block while a
                        // second mission still lacks a trustworthy server clock.
                        val strictRetention = app.patrolEvidenceRetentionManager.enforce()
                        if (strictRetention.isSuccess) {
                            Result.success(workDataOf(KEY_RETENTION_INCIDENT to true))
                        } else if (runAttemptCount >= MAX_RETRIES) {
                            CaptureController.applyFromSettings(app)
                            Result.failure()
                        } else {
                            CaptureController.applyFromSettings(app)
                            Result.retry()
                        }
                    } else if (runAttemptCount >= MAX_RETRIES) {
                        Result.failure()
                    } else {
                        Result.retry()
                    }
                } else if (runAttemptCount >= MAX_RETRIES) {
                    Result.failure()
                } else {
                    Result.retry()
                }
            },
        )
    }

    companion object {
        private const val UNIQUE_WORK = "patrolgrid_route_sync"
        private const val PERIODIC_WORK = "patrolgrid_route_sync_safety_net"
        private const val MAX_RETRIES = 12
        internal const val KEY_RETENTION_INCIDENT = "local_retention_incident"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<PatrolTrackSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun scheduleSafetyNet(context: Context) {
            val request = PeriodicWorkRequestBuilder<PatrolTrackSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
