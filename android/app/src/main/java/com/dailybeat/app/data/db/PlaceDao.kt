package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.Place
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    suspend fun all(): List<Place>

    @Query("SELECT * FROM places ORDER BY name ASC")
    fun observeAll(): Flow<List<Place>>

    @Insert
    suspend fun insert(place: Place): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(places: List<Place>)

    @Query("DELETE FROM places")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(place: Place)
}
