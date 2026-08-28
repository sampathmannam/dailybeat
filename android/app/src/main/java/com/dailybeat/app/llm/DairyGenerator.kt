package com.dailybeat.app.llm

import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class DairyGenerator(
    private val llm: LlmEngine,
    private val db: DailyBeatDb,
) {

    suspend fun generateForToday(): Result<String> = generateForDay(LocalDate.now())

    suspend fun generateForDay(date: LocalDate): Result<String> {
        val zone = ZoneId.systemDefault()
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = db.events().eventsForDay(startMs, endMs)
        if (events.isEmpty()) {
            return Result.failure(IllegalStateException("No events logged for this day."))
        }
        if (!llm.isModelAvailable()) {
            return Result.success(ruleBasedDairy(events, zone))
        }
        return llm.generate(buildDayPrompt(events, zone))
    }

    private fun ruleBasedDairy(events: List<Event>, zone: ZoneId): String {
        return events.joinToString(separator = " ") { event ->
            val time = Instant.ofEpochMilli(event.timestamp)
                .atZone(zone)
                .toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES)
            val place = event.placeName?.let { " at $it" } ?: ""
            "At $time hours$place, ${event.rawText.trim()}."
        }
    }

    private fun buildDayPrompt(events: List<Event>, zone: ZoneId): String {
        val eventsText = events.joinToString(separator = "\n") { event ->
            val time = Instant.ofEpochMilli(event.timestamp)
                .atZone(zone)
                .toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES)
                .toString()
            val place = event.placeName ?: "—"
            "[$time] $place: ${event.rawText}"
        }

        return """
            You are an Indian Police Service officer writing your official daily diary.
            Convert the following events of the day into a formal dairy entry in
            standard IPS dairy format. Use only the information given. Do not invent.
            Use 24-hour time. Keep entries concise.

            EVENTS OF THE DAY:
            $eventsText

            DAILY DIARY:
        """.trimIndent()
    }
}
