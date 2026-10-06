package com.dailybeat.app.capture

import com.dailybeat.app.data.db.RecordedFixTiming
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class RecordedGap(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

data class CaptureDayAudit(
    val date: LocalDate,
    val pointCount: Int,
    val approximatePointCount: Int,
    val firstPointMs: Long?,
    val lastPointMs: Long?,
    val gaps: List<RecordedGap>,
)

/** Absence of records is unknown time; it does not establish travel, stillness or a GPS failure. */
object CaptureGapAudit {
    const val GAP_THRESHOLD_MS = 10 * 60_000L

    fun days(input: List<RecordedFixTiming>, nowMs: Long, zone: ZoneId): List<CaptureDayAudit> {
        val points = input.filter { it.timestampMs in 1..nowMs }.sortedBy { it.timestampMs }
        if (points.isEmpty()) return emptyList()
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val byDate = points.groupBy { date(it.timestampMs) }
        val gapsByDate = mutableMapOf<LocalDate, MutableList<RecordedGap>>()
        points.zipWithNext().forEach { (previous, next) ->
            if (next.timestampMs - previous.timestampMs > GAP_THRESHOLD_MS) {
                var start = previous.timestampMs
                while (start < next.timestampMs) {
                    val day = date(start)
                    val boundary = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = minOf(next.timestampMs, boundary)
                    gapsByDate.getOrPut(day) { mutableListOf() }.add(RecordedGap(start, end))
                    start = end
                }
            }
        }
        val first = date(points.first().timestampMs)
        val last = date(points.last().timestampMs)
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { day ->
            val recorded = byDate[day].orEmpty()
            CaptureDayAudit(day, recorded.size,
                recorded.count { it.quality != "good" || !it.accuracyM.isFinite() || it.accuracyM > 75f },
                recorded.firstOrNull()?.timestampMs, recorded.lastOrNull()?.timestampMs,
                gapsByDate[day].orEmpty())
        }.toList()
    }
}
