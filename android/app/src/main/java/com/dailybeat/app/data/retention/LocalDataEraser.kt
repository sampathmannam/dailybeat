package com.dailybeat.app.data.retention

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.MotionStateStore
import com.dailybeat.app.capture.CaptureResumeWorker
import com.dailybeat.app.capture.SharedPreferencesVisitTrackerStateStore
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.util.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import com.dailybeat.app.capture.CaptureStorageGate

class LocalDataEraser(private val app: DailyBeatApp) {
    suspend fun erase() {
        val failures = mutableListOf<Throwable>()
        fun attempt(block: () -> Unit) {
            try {
                block()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failures += error
            }
        }

        attempt { app.mapSettings.resetForErase(); app.mapNetwork.cancelRequests() }
        try { app.offlineMaps.delete(); app.mapNetwork.clearCache(); app.mapNetwork.clearNativeCache() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failures += error }
        attempt { app.settingsRepository.setGpsEnabled(false) }
        CaptureController.stopForDataErase(app).exceptionOrNull()?.let(failures::add)
        attempt { CaptureResumeWorker.cancel(app) }
        attempt { PulseScheduler.cancel(app) }
        attempt {
            app.backupCoordinator.signOut()
            check(app.backupCoordinator.currentSession() == null) {
                "Unable to remove the cloud backup session from this phone."
            }
        }
        CaptureStorageGate.mutex.withLock {
        CaptureStorageGate.generation.incrementAndGet()
        withContext(Dispatchers.IO) {
            attempt { app.db.clearAllTables() }
            attempt { app.settingsRepository.secureApiKey.clearApiKey() }
            attempt { app.captureHealthStore.clear() }
            attempt { SharedPreferencesVisitTrackerStateStore(app).clearSynchronously() }
            attempt { MotionStateStore(app).clearSynchronously() }
            attempt { check(CaptureAuditLog.clear(app)) { "Unable to clear the capture audit log." } }
            attempt {
                check(OperationalFailureLog.clear(app)) { "Unable to clear operational diagnostics." }
            }
            attempt {
                val uncleared = AppStorage.outputDir(app).listFiles().orEmpty().filterNot {
                    AppStorage.clearSensitiveFileVerified(it)
                }
                check(uncleared.isEmpty()) { "Unable to clear ${uncleared.size} exported file(s)." }
            }
            attempt {
                val staging = java.io.File(app.cacheDir, "encrypted-backup-staging")
                check(!staging.exists() || staging.deleteRecursively()) { "Unable to clear backup staging files." }
            }
            attempt { app.settingsRepository.resetAfterLocalDataDeletion() }
        }
        }
        attempt { HistoryRetentionWorker.applySchedule(app, 0) }

        if (failures.isNotEmpty()) {
            val error = IllegalStateException(
                "Some local data could not be erased. Try again before handing over this phone.",
                failures.first(),
            )
            failures.drop(1).forEach(error::addSuppressed)
            throw error
        }
    }
}
