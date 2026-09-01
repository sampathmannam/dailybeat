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

    @Query("DELETE FROM patrol_track_points WHERE missionId = :missionId")
    suspend fun deleteForMission(missionId: String)
}
