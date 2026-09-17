package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.DiaryRevision
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

    @Query("SELECT * FROM diaries WHERE dateKey >= :from AND dateKey <= :through AND TRIM(text) != '' ORDER BY dateKey DESC")
    suspend fun nonEmptyBetween(from: String, through: String): List<DiaryEntry>

    @Query("SELECT COUNT(*) FROM diaries WHERE text != ''")
    suspend fun countNonEmpty(): Int

    @Query("SELECT * FROM diary_revisions ORDER BY id ASC")
    suspend fun allRevisions(): List<DiaryRevision>

    @Query("SELECT * FROM diary_revisions WHERE dateKey = :dateKey ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRevisions(dateKey: String, limit: Int): Flow<List<DiaryRevision>>

    @Query("SELECT * FROM diary_revisions WHERE dateKey = :dateKey ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestRevision(dateKey: String): DiaryRevision?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: DiaryRevision): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevisions(revisions: List<DiaryRevision>)

    @Query("DELETE FROM diary_revisions")
    suspend fun deleteAllRevisions()

    @Query("DELETE FROM diary_revisions WHERE dateKey < :cutoffDateKey")
    suspend fun deleteRevisionsBefore(cutoffDateKey: String): Int

    @Query("DELETE FROM diaries WHERE dateKey < :cutoffDateKey")
    suspend fun deleteBefore(cutoffDateKey: String): Int

    @Query(
        "DELETE FROM diary_revisions WHERE dateKey = :dateKey AND id NOT IN " +
            "(SELECT id FROM diary_revisions WHERE dateKey = :dateKey ORDER BY createdAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimRevisions(dateKey: String, keep: Int)
}
