package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Event
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DairyFormatter {

    fun formatEvents(events: List<Event>, zone: ZoneId = ZoneId.systemDefault()): String {
        if (events.isEmpty()) return ""
        return events.joinToString(separator = " ") { event ->
            val time = Instant.ofEpochMilli(event.timestamp)
                .atZone(zone)
                .toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES)
            val place = event.placeName?.let { " at $it" } ?: ""
            "At $time hours$place, ${event.rawText.trim()}."
        }
    }
}
