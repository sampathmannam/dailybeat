package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.DiaryDao
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DiaryRepository(private val diaryDao: DiaryDao) {

    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun observeToday(): Flow<DiaryEntry?> = diaryDao.observeForDate(todayKey())

    suspend fun saveToday(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        diaryDao.upsert(
            DiaryEntry(
                dateKey = todayKey(),
                text = trimmed,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun todayText(): String? = diaryDao.forDate(todayKey())?.text

    private fun todayKey(): String = LocalDate.now().format(formatter)
}
