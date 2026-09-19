package com.dailybeat.app.capture

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.util.PermissionHelper
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.withLock

/** Stops the active foreground service only after Android's STILL state has remained stable. */
class StillnessConfirmationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()
        val motion = MotionStateStore(app)
        if (
            CaptureController.canSleep(app) &&
            motion.state == MotionState.STILL &&
            AdaptiveCapturePolicy.shouldStopForStillness(
                stillSinceMs = motion.changedAtMs,
                nowMs = System.currentTimeMillis(),
                captureEnabled = settings.gpsCaptureEnabled &&
                    !app.settingsRepository.isCapturePaused() &&
                    PermissionHelper.canCaptureLocation(app),
            )
        ) {
            if (!CaptureController.hasConfirmedStay(app)) return Result.retry()
            CaptureStorageGate.mutex.withLock { app.captureProcessor.suspendCapture() }
            LocationService.stop(app)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "dailybeat-confirm-stillness"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<StillnessConfirmationWorker>()
                .setInitialDelay(AdaptiveCapturePolicy.STILL_CONFIRMATION_MS, TimeUnit.MILLISECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        fun cancel(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
            }
        }
    }
}
