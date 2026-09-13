package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.EventDao
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.StructuredEvent
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.DayBounds
import com.dailybeat.app.util.InputPolicy
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
        val trimmed = InputPolicy.multiline(rawText, InputPolicy.MOMENT_NOTE_CHARS).trim()
        if (trimmed.isEmpty()) return
        eventDao.insert(
            Event(
                timestamp = System.currentTimeMillis(),
                type = "manual",
                rawText = trimmed,
            ),
        )
    }

    suspend fun addMomentMarker(label: String = "Significant moment flagged") {
        val trimmed = InputPolicy.singleLine(label, InputPolicy.MOMENT_NOTE_CHARS).trim()
        if (trimmed.isEmpty()) return
        eventDao.insert(
            Event(
                timestamp = System.currentTimeMillis(),
                type = "moment",
                rawText = trimmed,
            ),
        )
    }

    suspend fun addStructuredEvent(structured: StructuredEvent, type: String = "voice") {
        val rawText = InputPolicy.multiline(structured.rawText, InputPolicy.MOMENT_NOTE_CHARS).trim()
        if (rawText.isEmpty()) return
        eventDao.insert(
            Event(
                timestamp = structured.timestamp,
                type = type.trim().take(MAX_TYPE_CHARS).ifBlank { "voice" },
                rawText = rawText,
                placeName = structured.placeName.bounded(MAX_PLACE_CHARS),
                peopleMentioned = structured.peopleMentioned.bounded(MAX_METADATA_CHARS),
                caseNumbers = structured.caseNumbers.bounded(MAX_METADATA_CHARS),
            ),
        )
    }

    suspend fun deleteEvent(event: Event) = eventDao.delete(event)

    companion object {
        private const val MAX_TYPE_CHARS = 32
        private const val MAX_PLACE_CHARS = 500
        private const val MAX_METADATA_CHARS = 1_000
    }

    private fun String?.bounded(maxChars: Int): String? = this
        ?.trim()
        ?.take(maxChars)
        ?.takeIf { it.isNotEmpty() }
}
