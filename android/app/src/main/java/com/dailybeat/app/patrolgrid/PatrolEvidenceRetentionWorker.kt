package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureController
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal enum class PatrolRetentionWorkOutcome { SUCCESS, RETRY, FAILURE }

internal fun patrolRetentionWorkOutcome(
    enforcementSucceeded: Boolean,
    runAttemptCount: Int,
    maxRetries: Int = PatrolEvidenceRetentionWorker.MAX_RETRIES,
): PatrolRetentionWorkOutcome = when {
    enforcementSucceeded -> PatrolRetentionWorkOutcome.SUCCESS
    runAttemptCount >= maxRetries -> PatrolRetentionWorkOutcome.FAILURE
    else -> PatrolRetentionWorkOutcome.RETRY
}

/** Robolectric uses one shared synthetic preference store across asynchronous tests. */
internal fun shouldSchedulePatrolRetentionWorkers(deviceFingerprint: String): Boolean =
    !deviceFingerprint.contains("robolectric", ignoreCase = true)

/** Connectivity-independent process-start and daily enforcement of local retention. */
class PatrolEvidenceRetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val enforcement = try {
            app.patrolEvidenceRetentionManager.enforce()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Covers secure-store construction failing before manager.enforce() begins.
            reportPatrolRetentionEnforcementFailure(
                settings = app.settingsRepository,
                occurredAtMs = System.currentTimeMillis(),
            )
            kotlin.Result.failure(error)
        }
        if (enforcement.isFailure) {
            // Persisted failure is also an immediate runtime stop signal. Otherwise an
            // already-running foreground service could keep collecting until next launch.
            CaptureController.applyFromSettings(app)
        }
        return when (
            patrolRetentionWorkOutcome(
                enforcementSucceeded = enforcement.isSuccess,
                runAttemptCount = runAttemptCount,
            )
        ) {
            PatrolRetentionWorkOutcome.SUCCESS -> Result.success(
                workDataOf(
                    KEY_RETENTION_INCIDENT to
                        enforcement.getOrThrow().hasIncident,
                ),
            )
            PatrolRetentionWorkOutcome.RETRY -> Result.retry()
            PatrolRetentionWorkOutcome.FAILURE -> Result.failure()
        }
    }

    companion object {
        internal const val MAX_RETRIES = 12
        internal const val KEY_RETENTION_INCIDENT = "local_retention_incident"
        internal const val PERIODIC_INTERVAL_HOURS = 24L
        private const val IMMEDIATE_WORK = "patrolgrid_local_retention_on_start"
        private const val PERIODIC_WORK = "patrolgrid_local_retention_daily"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(
                IMMEDIATE_WORK,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PatrolEvidenceRetentionWorker>().build(),
            )
            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PatrolEvidenceRetentionWorker>(
                    PERIODIC_INTERVAL_HOURS,
                    TimeUnit.HOURS,
                ).build(),
            )
        }
    }
}
