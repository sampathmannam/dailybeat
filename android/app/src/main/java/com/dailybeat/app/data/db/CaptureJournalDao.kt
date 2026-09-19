package com.dailybeat.app.data.db

import androidx.room.*

/** Unacknowledged device fixes. A fix is removed in the same transaction as its derived data. */
@Entity(tableName = "capture_inbox")
data class CaptureFix(
    @PrimaryKey val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val isMock: Boolean,
)

@Entity(tableName = "capture_checkpoint")
data class CaptureCheckpoint(@PrimaryKey val id: Int = 1, val payload: String)

@Dao
interface CaptureJournalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(fixes: List<CaptureFix>)
    @Query("SELECT * FROM capture_inbox ORDER BY timestampMs LIMIT 128")
    suspend fun pending(): List<CaptureFix>
    @Query("DELETE FROM capture_inbox WHERE timestampMs = :timestampMs")
    suspend fun acknowledge(timestampMs: Long)
    @Query("SELECT payload FROM capture_checkpoint WHERE id = 1")
    suspend fun checkpoint(): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun checkpoint(checkpoint: CaptureCheckpoint)
    @Query("DELETE FROM capture_inbox WHERE timestampMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long): Int
    @Query("DELETE FROM capture_inbox")
    suspend fun clearInbox()
    @Query("DELETE FROM capture_checkpoint")
    suspend fun clearCheckpoint()
}
