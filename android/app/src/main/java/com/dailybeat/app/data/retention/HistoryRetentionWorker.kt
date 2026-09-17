package com.dailybeat.app.data.retention

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import java.util.concurrent.TimeUnit

class HistoryRetentionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val days = app.settingsRepository.get().historyRetentionDays
        if (days == 0) return Result.success()
        return runCatching {
            app.historyRetentionManager.prune(days)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_NAME = "dailybeat-history-retention"

        fun applySchedule(context: Context, days: Int) {
            if (days == 0) {
                // Some local JVM tests intentionally omit WorkManager initialization. Cancellation
                // is best effort and a disabled retention policy never needs a worker to run.
                runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
                return
            }
            val workManager = WorkManager.getInstance(context)
            val request = PeriodicWorkRequestBuilder<HistoryRetentionWorker>(24, TimeUnit.HOURS).build()
            workManager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
