package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.dailybeat.app.data.model.Event
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    fun observeEventsBetween(start: Long, end: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun eventsForDay(start: Long, end: Long): List<Event>

    @Insert
    suspend fun insert(event: Event): Long

    @Update
    suspend fun update(event: Event)

    @Delete
    suspend fun delete(event: Event)
}
