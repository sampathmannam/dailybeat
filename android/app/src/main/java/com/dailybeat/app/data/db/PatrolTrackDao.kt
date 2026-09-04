package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dailybeat.app.data.model.PatrolTrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface PatrolTrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: PatrolTrackPoint): Long

    @Query("SELECT * FROM patrol_track_points WHERE missionId = :missionId ORDER BY timestampMs ASC")
    suspend fun forMission(missionId: String): List<PatrolTrackPoint>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM patrol_track_points WHERE missionId = :missionId " +
            "ORDER BY timestampMs DESC, id DESC LIMIT :limit" +
            ") ORDER BY timestampMs ASC, id ASC",
    )
    suspend fun latestForMission(missionId: String, limit: Int): List<PatrolTrackPoint>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM patrol_track_points WHERE missionId = :missionId AND sessionId = :sessionId " +
            "ORDER BY timestampMs DESC, id DESC LIMIT :limit" +
            ") ORDER BY timestampMs ASC, id ASC",
    )
    suspend fun latestForSession(
        missionId: String,
        sessionId: String,
        limit: Int,
    ): List<PatrolTrackPoint>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM patrol_track_points WHERE missionId = :missionId " +
            "ORDER BY timestampMs DESC, id DESC LIMIT :limit" +
            ") ORDER BY timestampMs ASC, id ASC",
    )
    fun observeLatestForMission(missionId: String, limit: Int): Flow<List<PatrolTrackPoint>>

    @Query(
        "SELECT * FROM (" +
            "SELECT * FROM patrol_track_points WHERE missionId = :missionId AND sessionId = :sessionId " +
            "ORDER BY timestampMs DESC, id DESC LIMIT :limit" +
            ") ORDER BY timestampMs ASC, id ASC",
    )
    fun observeLatestForSession(
        missionId: String,
        sessionId: String,
        limit: Int,
    ): Flow<List<PatrolTrackPoint>>

    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE missionId = :missionId")
    suspend fun countForMission(missionId: String): Int

    @Query(
        "SELECT COUNT(*) FROM patrol_track_points " +
            "WHERE missionId = :missionId AND sessionId = :sessionId",
    )
    suspend fun countForSession(missionId: String, sessionId: String): Int

    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE missionId = :missionId")
    fun observeCountForMission(missionId: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM patrol_track_points " +
            "WHERE missionId = :missionId AND sessionId = :sessionId",
    )
    fun observeCountForSession(missionId: String, sessionId: String): Flow<Int>

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

    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE missionId IN (:missionIds)")
    suspend fun countForMissions(missionIds: List<String>): Int

    @Query("SELECT DISTINCT missionId FROM patrol_track_points WHERE sessionId IS NOT NULL")
    suspend fun accountOwnedMissionIds(): List<String>

    @Query("DELETE FROM patrol_track_points WHERE missionId IN (:missionIds)")
    suspend fun deleteForMissionsRaw(missionIds: List<String>): Int

    /** Count and delete share one Room transaction after the cross-store intent journal exists. */
    @Transaction
    suspend fun deleteForMissions(missionIds: List<String>): Int {
        if (missionIds.isEmpty()) return 0
        val count = countForMissions(missionIds)
        val deleted = deleteForMissionsRaw(missionIds)
        check(deleted == count) { "Patrol route evidence changed during retention cleanup." }
        return deleted
    }

    /** Route rows tied to a hosted session remain account-owned even after upload. */
    @Query("SELECT COUNT(*) FROM patrol_track_points WHERE sessionId IS NOT NULL")
    suspend fun accountOwnedEvidenceCount(): Int

    @Query("DELETE FROM patrol_track_points WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}
