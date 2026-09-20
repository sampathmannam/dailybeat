package com.dailybeat.app.data.retention

import android.content.Context
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.util.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Shares the erasure gate so an upgrade cannot recreate exports after phone data was erased. */
object PrivateStorageMigration {
    suspend fun migrate(context: Context): Boolean = withContext(Dispatchers.IO) {
        CaptureStorageGate.mutex.withLock {
            val exportsMoved = AppStorage.migrateLegacyOutputs(context)
            val auditMoved = CaptureAuditLog.migrateLegacyStorage(context)
            exportsMoved && auditMoved
        }
    }
}
