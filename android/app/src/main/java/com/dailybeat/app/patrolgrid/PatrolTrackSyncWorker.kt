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
import com.dailybeat.app.DailyBeatApp
import java.util.concurrent.TimeUnit

class PatrolTrackSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val syncResult = app.patrolActionOutbox.syncPending().fold(
            onSuccess = { app.patrolTrackSyncer.syncAndClosePendingSession() },
            onFailure = { kotlin.Result.failure(it) },
        )
        return syncResult.fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount >= MAX_RETRIES) Result.failure() else Result.retry()
            },
        )
    }

    companion object {
        private const val UNIQUE_WORK = "patrolgrid_route_sync"
        private const val PERIODIC_WORK = "patrolgrid_route_sync_safety_net"
        private const val MAX_RETRIES = 12

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
