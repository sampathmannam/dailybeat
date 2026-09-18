package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.DiaryDao
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.data.model.DiaryRevision
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.InputPolicy
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DiaryRepository(private val diaryDao: DiaryDao) {

    fun observeToday(): Flow<DiaryEntry?> = observeForDate(DateKeys.today())

    fun observeForDate(date: LocalDate): Flow<DiaryEntry?> =
        diaryDao.observeForDate(DateKeys.format(date))

    fun observeRecent(limit: Int = 30): Flow<List<DiaryEntry>> = diaryDao.observeRecent(limit)

    fun observeRevisions(date: LocalDate, limit: Int = 20): Flow<List<DiaryRevision>> =
        diaryDao.observeRevisions(DateKeys.format(date), limit.coerceIn(1, MAX_REVISIONS_PER_DAY))

    suspend fun saveToday(text: String) = saveForDate(DateKeys.today(), text)

    suspend fun saveForDate(date: LocalDate, text: String) {
        val trimmed = InputPolicy.multiline(text, InputPolicy.DIARY_CHARS).trim()
        // An empty body is a deliberate clear, not a no-op; dropping it made the old text
        // reappear the next time the day was opened.
        diaryDao.upsert(
            DiaryEntry(
                dateKey = DateKeys.format(date),
                text = trimmed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun checkpointForDate(date: LocalDate, reason: String) {
        val dateKey = DateKeys.format(date)
        val current = diaryDao.forDate(dateKey) ?: return
        if (current.text.isBlank() || diaryDao.latestRevision(dateKey)?.text == current.text) return
        diaryDao.insertRevision(
            DiaryRevision(
                dateKey = dateKey,
                text = current.text,
                createdAt = System.currentTimeMillis(),
                reason = InputPolicy.singleLine(reason, 80).ifBlank { "Saved version" },
            ),
        )
        diaryDao.trimRevisions(dateKey, MAX_REVISIONS_PER_DAY)
    }

    suspend fun restoreRevision(date: LocalDate, revision: DiaryRevision) {
        require(revision.dateKey == DateKeys.format(date)) { "That version belongs to another day." }
        checkpointForDate(date, "Before restoring a previous version")
        saveForDate(date, revision.text)
    }

    suspend fun todayText(): String? = textForDate(DateKeys.today())

    suspend fun textForDate(date: LocalDate): String? =
        diaryDao.forDate(DateKeys.format(date))?.text

    suspend fun countNonEmpty(): Int = diaryDao.countNonEmpty()

    suspend fun weekEnding(date: LocalDate): List<DiaryEntry> =
        diaryDao.nonEmptyBetween(DateKeys.format(date.minusDays(6)), DateKeys.format(date))

    private companion object {
        const val MAX_REVISIONS_PER_DAY = 50
    }
}
