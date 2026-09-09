package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dailybeat.app.data.model.BeatReview
import kotlinx.coroutines.flow.Flow

@Dao
interface BeatReviewDao {
    @Query("SELECT * FROM beat_reviews ORDER BY dateKey DESC")
    suspend fun all(): List<BeatReview>

    @Query("SELECT * FROM beat_reviews WHERE dateKey = :dateKey LIMIT 1")
    fun observe(dateKey: String): Flow<BeatReview?>

    @Query("SELECT * FROM beat_reviews WHERE dateKey = :dateKey LIMIT 1")
    suspend fun get(dateKey: String): BeatReview?

    @Query("SELECT * FROM beat_reviews WHERE dateKey BETWEEN :startDate AND :endDate ORDER BY dateKey DESC")
    suspend fun between(startDate: String, endDate: String): List<BeatReview>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: BeatReview)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(reviews: List<BeatReview>)

    @Query("DELETE FROM beat_reviews")
    suspend fun deleteAll()
}
