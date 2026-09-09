package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.BeatReviewDao
import com.dailybeat.app.data.model.BeatReview
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class BeatRepository(
    private val dao: BeatReviewDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(date: LocalDate): Flow<BeatReview?> = dao.observe(DateKeys.format(date))
    suspend fun get(date: LocalDate): BeatReview? = dao.get(DateKeys.format(date))
    suspend fun all(): List<BeatReview> = dao.all()

    suspend fun saveTitle(date: LocalDate, title: String) {
        val current = get(date)
        dao.upsert(
            (current ?: BeatReview(dateKey = DateKeys.format(date))).copy(
                title = title.trim().take(120),
                updatedAt = clock(),
            ),
        )
    }

    suspend fun complete(date: LocalDate, title: String) {
        val now = clock()
        val current = get(date)
        dao.upsert(
            (current ?: BeatReview(dateKey = DateKeys.format(date))).copy(
                title = title.trim().take(120),
                state = "complete",
                completedAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun reopen(date: LocalDate) {
        val current = get(date) ?: return
        dao.upsert(current.copy(state = "needs_review", completedAt = null, updatedAt = clock()))
    }
}
