package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.PatrolTrackPoint

@Dao
interface PatrolTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: PatrolTrackPoint): Long

    @Query("SELECT * FROM patrol_track_points WHERE missionId = :missionId ORDER BY timestampMs ASC")
    suspend fun forMission(missionId: String): List<PatrolTrackPoint>

    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE missionId = :missionId")
    suspend fun countForMission(missionId: String): Int

    @Query(
        "SELECT * FROM patrol_track_points " +
            "WHERE syncedAtMs IS NULL AND sessionId IS NOT NULL AND clientPointId IS NOT NULL " +
            "AND (:missionId IS NULL OR missionId = :missionId) ORDER BY id ASC LIMIT :limit",
    )
    suspend fun pending(missionId: String? = null, limit: Int = 250): List<PatrolTrackPoint>

    @Query("UPDATE patrol_track_points SET syncedAtMs = :syncedAtMs WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, syncedAtMs: Long)

    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE syncedAtMs IS NULL AND sessionId IS NOT NULL")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM patrol_track_points WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}
