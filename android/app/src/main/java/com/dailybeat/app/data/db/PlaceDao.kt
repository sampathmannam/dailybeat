package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.dailybeat.app.data.model.Place

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name ASC")
    suspend fun all(): List<Place>

    @Insert
    suspend fun insert(place: Place): Long
}
