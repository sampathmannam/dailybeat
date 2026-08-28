package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.EventDao
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.StructuredEvent
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class EventRepository(private val eventDao: EventDao) {

    fun observeTodayEvents(): Flow<List<Event>> = observeEventsForDate(DateKeys.today())

    fun observeEventsForDate(date: LocalDate): Flow<List<Event>> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return eventDao.observeEventsBetween(start, end)
    }

    suspend fun eventsForDate(date: LocalDate): List<Event> {
        val (start, end) = DayBounds.dayStartEnd(date)
        return eventDao.eventsForDay(start, end)
    }

    suspend fun countToday(): Int = eventsForDate(DateKeys.today()).size

    suspend fun addManualEvent(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        eventDao.insert(
            Event(
                timestamp = System.currentTimeMillis(),
                type = "manual",
                rawText = trimmed,
            ),
        )
    }

    suspend fun addStructuredEvent(structured: StructuredEvent, type: String = "voice") {
        eventDao.insert(
            Event(
                timestamp = structured.timestamp,
                type = type,
                rawText = structured.rawText,
                placeName = structured.placeName,
                peopleMentioned = structured.peopleMentioned,
                caseNumbers = structured.caseNumbers,
            ),
        )
    }

    suspend fun updateEvent(event: Event) = eventDao.update(event)

    suspend fun deleteEvent(event: Event) = eventDao.delete(event)

    suspend fun todayEventsText(): String = eventsTextForDate(DateKeys.today())

    suspend fun eventsTextForDate(date: LocalDate): String {
        return eventsForDate(date).joinToString(separator = "\n") { event -> event.rawText }
    }
}
