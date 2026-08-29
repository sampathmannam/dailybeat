package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.DiaryDao
import com.dailybeat.app.data.model.DiaryEntry
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class DiaryRepository(private val diaryDao: DiaryDao) {

    fun observeToday(): Flow<DiaryEntry?> = observeForDate(DateKeys.today())

    fun observeForDate(date: LocalDate): Flow<DiaryEntry?> =
        diaryDao.observeForDate(DateKeys.format(date))

    fun observeRecent(limit: Int = 30): Flow<List<DiaryEntry>> = diaryDao.observeRecent(limit)

    suspend fun saveToday(text: String) = saveForDate(DateKeys.today(), text)

    suspend fun saveForDate(date: LocalDate, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        diaryDao.upsert(
            DiaryEntry(
                dateKey = DateKeys.format(date),
                text = trimmed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun todayText(): String? = textForDate(DateKeys.today())

    suspend fun textForDate(date: LocalDate): String? =
        diaryDao.forDate(DateKeys.format(date))?.text

    suspend fun countNonEmpty(): Int = diaryDao.countNonEmpty()

    suspend fun recentSync(limit: Int = 30): List<DiaryEntry> = diaryDao.recent(limit)
}
