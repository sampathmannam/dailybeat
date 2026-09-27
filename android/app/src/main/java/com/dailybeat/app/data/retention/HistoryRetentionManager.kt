package com.dailybeat.app.data.retention

import androidx.room.withTransaction
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.capture.BufferedCheckpoint
import com.dailybeat.app.data.db.CaptureCheckpoint
import kotlinx.coroutines.sync.withLock
import com.dailybeat.app.data.db.DailyBeatDb
import java.time.LocalDate
import java.time.ZoneId

data class RetentionResult(
    val recordsDeleted: Int,
    val cutoffDate: LocalDate,
)

class HistoryRetentionManager(
    private val db: DailyBeatDb,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun prune(days: Int): RetentionResult = CaptureStorageGate.mutex.withLock {
        val result = pruneInsideCaptureLock(days)
        // Invalidate delayed writes only after the deletion commits, while the writer gate is
        // still held. The restore path calls the internal method and publishes after its own
        // outer transaction, so a rolled-back restore never emits a premature change.
        if (result.recordsDeleted > 0) CaptureStorageGate.invalidatePersonalData(retainedFrom = result.cutoffDate)
        result
    }

    internal suspend fun pruneInsideCaptureLock(days: Int): RetentionResult {
        require(days in setOf(30, 90, 365)) { "Retention must be 30, 90 or 365 days." }
        val nowMs = clock()
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
        val cutoffDate = today.minusDays(days.toLong() - 1L)
        val cutoffMs = cutoffDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val cutoffDateKey = cutoffDate.toString()
        val deleted = db.withTransaction {
            val payload = db.captureJournal().checkpoint()
            val checkpoint = BufferedCheckpoint(payload, nowMs).load()
            if (payload != null && payload != "{}" && (checkpoint == null || checkpoint.lastSampleMs < cutoffMs)) {
                // Unreadable checkpoints can still contain private coordinates. Keep an empty
                // marker so service startup cannot re-import an obsolete legacy checkpoint.
                db.captureJournal().checkpoint(CaptureCheckpoint(payload = "{}"))
            }
            db.captureJournal().deleteBefore(cutoffMs) + db.events().deleteBefore(cutoffMs) +
                db.visits().deleteCorrectionsBefore(cutoffMs) +
                db.visits().deleteCorrectionsForVisitsBefore(cutoffMs) +
                db.visits().deleteBefore(cutoffMs) +
                db.breadcrumbs().deleteBefore(cutoffMs) +
                db.diaries().deleteBefore(cutoffDateKey) +
                db.diaries().deleteRevisionsBefore(cutoffDateKey) +
                db.beatReviews().deleteBefore(cutoffDateKey)
        }
        return RetentionResult(deleted, cutoffDate)
    }
}
