package com.dailybeat.app.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp

/**
 * Runs the midday pulse off the alarm's broadcast, which is far too short-lived for a cloud
 * round trip, and waits for connectivity instead of failing silently while offline.
 */
class MiddayPulseWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()
        if (!settings.autoMiddayPulse || !app.settingsRepository.isCloudBrainReady()) {
            return Result.success()
        }

        return app.pulseGenerator.generateAndSavePulse().fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure() },
        )
    }

    companion object {
        private const val WORK_NAME = "midday_pulse"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<MiddayPulseWorker>()
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            } catch (_: IllegalStateException) {
                // WorkManager unavailable in tests.
            }
        }
    }
}
