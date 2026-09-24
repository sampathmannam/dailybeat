package com.dailybeat.app.cloud

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class ReportRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val dateKey = inputData.getString(KEY_DATE) ?: DateKeys.today().toString()
        val date = DateKeys.parseOrToday(dateKey)

        val generation = runAutomaticReportIfAllowed(
            automaticEnabled = { app.settingsRepository.get().autoEveningReport },
            cloudReady = { app.settingsRepository.isCloudBrainReady() },
            generate = { app.reportGenerator.generateUnattendedForDate(date) },
        ) ?: return Result.success()
        return generation.fold(
            onSuccess = { Result.success() },
            onFailure = { error ->
                if (ReportRetryPolicy.shouldRetry(error) && runAttemptCount < MAX_ATTEMPTS - 1) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            },
        )
    }

    companion object {
        private const val KEY_DATE = "date_key"
        private const val MAX_ATTEMPTS = 3

        /** One chain per day, so a retry for yesterday never cancels today's report. */
        private fun workNameFor(date: LocalDate): String = "report_retry_$date"

        /** The class tag also exists on work queued by older app versions. Manual diary
         * generation never uses this worker and is deliberately unaffected. */
        fun cancelAutomatic(context: Context) {
            try {
                WorkManager.getInstance(context).cancelAllWorkByTag(ReportRetryWorker::class.java.name)
            } catch (_: IllegalStateException) {
                // The current-consent entry gate still denies work if WorkManager is unavailable.
            }
        }

        fun enqueue(context: Context, date: LocalDate = DateKeys.today()) {
            try {
                val request = OneTimeWorkRequestBuilder<ReportRetryWorker>()
                    // The report is a cloud call; without this the retries burn out while the
                    // phone is offline and the day's report is lost for good.
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                    )
                    .setInputData(
                        androidx.work.workDataOf(KEY_DATE to date.toString()),
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    workNameFor(date),
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            } catch (_: IllegalStateException) {
                // WorkManager unavailable in tests.
            }
        }
    }
}

internal suspend fun runAutomaticReportIfAllowed(
    automaticEnabled: () -> Boolean,
    cloudReady: () -> Boolean,
    generate: suspend () -> kotlin.Result<String>,
): kotlin.Result<String>? {
    if (!automaticEnabled() || !cloudReady()) return null
    return try {
        generate()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        kotlin.Result.failure(error)
    }
}
