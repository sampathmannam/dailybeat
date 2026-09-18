package com.dailybeat.app.data.retention

import androidx.room.withTransaction
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
    suspend fun prune(days: Int): RetentionResult {
        require(days in setOf(30, 90, 365)) { "Retention must be 30, 90 or 365 days." }
        val today = java.time.Instant.ofEpochMilli(clock()).atZone(zoneId).toLocalDate()
        val cutoffDate = today.minusDays(days.toLong() - 1L)
        val cutoffMs = cutoffDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val cutoffDateKey = cutoffDate.toString()
        val deleted = db.withTransaction {
            db.events().deleteBefore(cutoffMs) +
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
