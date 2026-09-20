package com.dailybeat.app.capture

import android.content.Context
import androidx.work.*
import com.dailybeat.app.DailyBeatApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/** Retries storage only. Never starts GPS or invents fixes from a background worker. */
class CaptureRecoveryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val app = applicationContext as DailyBeatApp
        CaptureStorageGate.mutex.withLock {
            app.captureProcessor.drain(
                onRejected = app.captureHealthStore::rejected,
                onAccepted = app.captureHealthStore::accepted,
                allowNetworkLookup = app.settingsRepository.get().gpsCaptureEnabled &&
                    !app.settingsRepository.isCapturePaused() &&
                    com.dailybeat.app.util.PermissionHelper.hasLocation(app),
            )
        }
        Result.success()
    } catch (error: CancellationException) { throw error
    } catch (_: Exception) { Result.retry() }
    companion object {
        fun schedule(context: Context) {
            runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork("dailybeat-capture-recovery", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CaptureRecoveryWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            } // Startup without WorkManager must remain usable; the durable inbox retries on the next fix.
        }
    }
}
