package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.GeocodeCache

@Dao
interface GeocodeDao {
    @Query("SELECT * FROM geocode_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): GeocodeCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: GeocodeCache)
}
