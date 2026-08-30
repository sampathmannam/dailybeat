package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diaries ORDER BY dateKey ASC")
    suspend fun all(): List<DiaryEntry>

    @Query("SELECT * FROM diaries WHERE dateKey = :dateKey LIMIT 1")
    fun observeForDate(dateKey: String): Flow<DiaryEntry?>

    @Query("SELECT * FROM diaries WHERE dateKey = :dateKey LIMIT 1")
    suspend fun forDate(dateKey: String): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DiaryEntry)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<DiaryEntry>)

    @Query("DELETE FROM diaries")
    suspend fun deleteAll()

    @Query("SELECT * FROM diaries ORDER BY dateKey DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diaries ORDER BY dateKey DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<DiaryEntry>

    @Query("SELECT COUNT(*) FROM diaries WHERE text != ''")
    suspend fun countNonEmpty(): Int
}
