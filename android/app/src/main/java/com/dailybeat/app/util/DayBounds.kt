package com.dailybeat.app.util

import java.time.LocalDate
import java.time.ZoneId

object DayBounds {
    fun todayStartEnd(): Pair<Long, Long> = dayStartEnd(DateKeys.today())

    fun dayStartEnd(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return startMs to endMs
    }
}
