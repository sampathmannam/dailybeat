package com.dailybeat.app.ui.feed

import androidx.room.withTransaction
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.util.DayBounds
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Six bounded range queries per page, independent of how many dates it contains. */
class DayFeedLoader(private val db: DailyBeatDb) {
    suspend fun load(through: LocalDate, days: Int = 30, zone: ZoneId = ZoneId.systemDefault()): List<DayFeedItem> {
        require(days in 1..31)
        val from = through.minusDays(days - 1L)
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = through.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return db.withTransaction {
            val places = db.places().all()
            val visits = db.visits().between(start, end)
            val points = db.breadcrumbs().between(start, end).groupBy { Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() }
            val notes = db.events().eventsForDay(start, end).filter { it.type != "visit" }
                .groupingBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.eachCount()
            val diaries = db.diaries().nonEmptyBetween(from.toString(), through.toString()).associateBy { it.dateKey }
            val reviews = db.beatReviews().between(from.toString(), through.toString()).associateBy { it.dateKey }
            (0 until days).map { through.minusDays(it.toLong()) }.map { date ->
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                DayFeedBuilder.build(date = date, places = places,
                    visits = visits.filter { it.endMs >= dayStart && it.startMs <= dayEnd }
                        .map { it.copy(startMs = maxOf(it.startMs, dayStart), endMs = minOf(it.endMs, dayEnd)) },
                    diaryText = diaries[date.toString()]?.text.orEmpty(), breadcrumbs = points[date].orEmpty(),
                    review = reviews[date.toString()], noteCount = notes[date] ?: 0)
            }.filterNot { it.isEmpty }
        }
    }
}
