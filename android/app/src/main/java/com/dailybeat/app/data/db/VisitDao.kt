package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dailybeat.app.data.model.LocationVisit
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM location_visits WHERE startMs BETWEEN :start AND :end ORDER BY startMs ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<LocationVisit>>

    @Query("SELECT * FROM location_visits WHERE startMs BETWEEN :start AND :end ORDER BY startMs ASC")
    suspend fun between(start: Long, end: Long): List<LocationVisit>

    @Insert
    suspend fun insert(visit: LocationVisit): Long

    @Query("SELECT * FROM location_visits WHERE endMs = 0 LIMIT 1")
    suspend fun openVisit(): LocationVisit?
}
