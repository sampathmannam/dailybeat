package com.dailybeat.app.cloud

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate

class ReportRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        if (!app.settingsRepository.isCloudBrainReady()) return Result.success()

        val dateKey = inputData.getString(KEY_DATE) ?: DateKeys.today().toString()
        val date = DateKeys.parseOrToday(dateKey)

        return app.reportGenerator.generateAndSaveForDate(date).fold(
            onSuccess = { Result.success() },
            onFailure = {
                if (runAttemptCount < 2) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        private const val WORK_NAME = "report_retry"
        private const val KEY_DATE = "date_key"

        fun enqueue(context: Context, date: LocalDate = DateKeys.today()) {
            try {
                val request = OneTimeWorkRequestBuilder<ReportRetryWorker>()
                    .setInputData(
                        androidx.work.workDataOf(KEY_DATE to date.toString()),
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
