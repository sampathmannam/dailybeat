package com.dailybeat.app.data.retention

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.CaptureResumeWorker
import com.dailybeat.app.notify.PulseScheduler
import com.dailybeat.app.util.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalDataEraser(private val app: DailyBeatApp) {
    suspend fun erase() {
        app.settingsRepository.setGpsEnabled(false)
        CaptureController.applyFromSettings(app)
        CaptureResumeWorker.cancel(app)
        PulseScheduler.cancel(app)
        app.backupCoordinator.signOut()
        withContext(Dispatchers.IO) {
            app.db.clearAllTables()
            app.settingsRepository.secureApiKey.clearApiKey()
            CaptureAuditLog.clear(app)
            AppStorage.outputDir(app).listFiles().orEmpty().forEach(AppStorage::clearSensitiveFile)
            app.settingsRepository.resetAfterLocalDataDeletion()
        }
        HistoryRetentionWorker.applySchedule(app, 0)
    }
}
