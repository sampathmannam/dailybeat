package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.LocationBreadcrumb
import kotlinx.coroutines.flow.Flow

@Dao
interface BreadcrumbDao {
    @Query("SELECT * FROM location_breadcrumbs ORDER BY timestampMs ASC")
    suspend fun all(): List<LocationBreadcrumb>

    @Query("SELECT * FROM location_breadcrumbs WHERE timestampMs BETWEEN :start AND :end ORDER BY timestampMs ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<LocationBreadcrumb>>

    @Query("SELECT * FROM location_breadcrumbs WHERE timestampMs BETWEEN :start AND :end ORDER BY timestampMs ASC")
    suspend fun between(start: Long, end: Long): List<LocationBreadcrumb>

    @Query("SELECT * FROM location_breadcrumbs ORDER BY timestampMs DESC LIMIT 1")
    suspend fun latest(): LocationBreadcrumb?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: LocationBreadcrumb): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(points: List<LocationBreadcrumb>)

    @Query("DELETE FROM location_breadcrumbs")
    suspend fun deleteAll()
}
