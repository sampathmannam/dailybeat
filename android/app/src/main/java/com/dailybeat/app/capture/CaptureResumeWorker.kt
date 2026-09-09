package com.dailybeat.app.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import java.util.concurrent.TimeUnit

class CaptureResumeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        if (app.settingsRepository.capturePausedUntilMs() > System.currentTimeMillis()) {
            return Result.retry()
        }
        app.settingsRepository.clearCapturePause()
        CaptureController.applyFromSettings(app)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "dailybeat-capture-resume"

        fun schedule(context: Context, resumeAtMs: Long) {
            val delay = (resumeAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<CaptureResumeWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        fun cancel(context: Context) {
            runCatching { WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME) }
        }
    }
}
