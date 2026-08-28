package com.dailybeat.app.util

import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

object DayBounds {
    fun todayStartEnd(): Pair<Long, Long> = dayStartEnd(DateKeys.today())

    fun dayStartEnd(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return startMs to endMs
    }

    /** Legacy calendar-based bounds for a specific local date. */
    fun dayStartEndLegacy(date: LocalDate): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }
}
