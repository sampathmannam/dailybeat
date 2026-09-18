package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.VisitCorrection
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {
    @Query("SELECT * FROM location_visits ORDER BY id ASC")
    suspend fun all(): List<LocationVisit>

    @Query("SELECT * FROM location_visits WHERE hidden = 1 ORDER BY id ASC")
    suspend fun hiddenEntries(): List<LocationVisit>

    @Query("SELECT * FROM location_visits WHERE endMs >= :start AND startMs <= :end ORDER BY startMs ASC")
    fun observeBetween(start: Long, end: Long): Flow<List<LocationVisit>>

    @Query("SELECT * FROM location_visits WHERE endMs >= :start AND startMs <= :end ORDER BY startMs ASC")
    suspend fun between(start: Long, end: Long): List<LocationVisit>

    @Insert
    suspend fun insert(visit: LocationVisit): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(visits: List<LocationVisit>)

    @Update
    suspend fun update(visit: LocationVisit)

    @Query("UPDATE location_visits SET placeName = :name, manuallyEdited = 1, reviewState = 'confirmed' WHERE id = :id AND placeName IS :expectedName")
    suspend fun rename(id: Long, expectedName: String?, name: String): Int

    @Query("UPDATE location_visits SET hidden = :hidden, manuallyEdited = 1 WHERE id = :id AND hidden = :expectedHidden")
    suspend fun setHidden(id: Long, expectedHidden: Boolean, hidden: Boolean): Int

    @Query("SELECT * FROM visit_corrections ORDER BY id ASC")
    suspend fun allCorrections(): List<VisitCorrection>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrection(correction: VisitCorrection): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCorrections(corrections: List<VisitCorrection>)

    @Query("DELETE FROM visit_corrections")
    suspend fun deleteAllCorrections()

    @Query("DELETE FROM visit_corrections WHERE correctedAt < :cutoffMs")
    suspend fun deleteCorrectionsBefore(cutoffMs: Long): Int

    @Query(
        "DELETE FROM visit_corrections WHERE visitId IN " +
            "(SELECT id FROM location_visits WHERE endMs < :cutoffMs)",
    )
    suspend fun deleteCorrectionsForVisitsBefore(cutoffMs: Long): Int

    @Query("DELETE FROM location_visits WHERE endMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long): Int

    @Query("DELETE FROM location_visits")
    suspend fun deleteAll()
}
