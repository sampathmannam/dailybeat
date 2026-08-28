package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.EventDao
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {

    fun observeTodayEvents(): Flow<List<Event>> {
        val (start, end) = DayBounds.todayStartEnd()
        return eventDao.observeEventsBetween(start, end)
    }

    suspend fun addManualEvent(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return
        }
        eventDao.insert(
            Event(
                timestamp = System.currentTimeMillis(),
                type = "manual",
                rawText = trimmed,
            ),
        )
    }

    suspend fun todayEventsText(): String {
        val (start, end) = DayBounds.todayStartEnd()
        return eventDao.eventsForDay(start, end)
            .joinToString(separator = "\n") { event -> event.rawText }
    }
}
