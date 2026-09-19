package com.dailybeat.app.data.db

import androidx.room.*
import com.dailybeat.app.data.model.*

@Dao
interface BackupPageDao {
    @Query("SELECT * FROM events WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 8")
    suspend fun events(after: Long?): List<Event>
    @Query("SELECT * FROM places WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 500")
    suspend fun places(after: Long?): List<Place>
    @Query("SELECT * FROM diaries WHERE dateKey > :after ORDER BY dateKey LIMIT 8")
    suspend fun diaries(after: String): List<DiaryEntry>
    @Query("SELECT * FROM location_visits WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 500")
    suspend fun visits(after: Long?): List<LocationVisit>
    @Query("SELECT * FROM location_breadcrumbs WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 1000")
    suspend fun breadcrumbs(after: Long?): List<LocationBreadcrumb>
    @Query("SELECT * FROM beat_reviews WHERE dateKey > :after ORDER BY dateKey LIMIT 500")
    suspend fun beatReviews(after: String): List<BeatReview>
    @Query("SELECT * FROM diary_revisions WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 8")
    suspend fun diaryRevisions(after: Long?): List<DiaryRevision>
    @Query("SELECT * FROM visit_corrections WHERE (:after IS NULL OR id > :after) ORDER BY id LIMIT 500")
    suspend fun visitCorrections(after: Long?): List<VisitCorrection>
}
