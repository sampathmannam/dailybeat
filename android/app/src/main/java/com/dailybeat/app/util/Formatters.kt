package com.dailybeat.app.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The one place a number becomes words.
 *
 * Before this existed the same day read "~4 km · 3h 34m" on Today and "4.0 km · 3 h 34 min" on
 * Days, and a 50 m day was "50 m", "0.1 km" or "—" depending on the screen. Every screen now goes
 * through here, so a value looks the same wherever the officer meets it.
 *
 * - Distance under a kilometre is whole metres ("850 m"); from a kilometre up it is one decimal
 *   ("4.0 km", "12.5 km"). A leading "~" marks an estimate — some points were unreliable and were
 *   left out of the total.
 * - Duration has two deliberate forms and a screen never mixes them for the same role:
 *   [duration] for prose and timeline rows ("2 h 15 min"), [durationCompact] for stat tiles where
 *   width is scarce ("2h 15m").
 * - Clock times are always 24-hour. The official diary is written in 24-hour time and the model is
 *   asked for it, so the app matches the document it produces.
 */
object Formatters {
    private const val CLOCK_PATTERN = "HH:mm"
    private const val DAY_HEADING_PATTERN = "EEEE, d MMMM"
    private const val DAY_HEADING_WITH_YEAR_PATTERN = "EEEE, d MMMM yyyy"

    fun distance(
        meters: Double,
        estimated: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val safe = if (meters.isFinite()) meters.coerceAtLeast(0.0) else 0.0
        val value = if (safe < 1_000) {
            String.format(locale, "%d m", safe.roundToInt())
        } else {
            String.format(locale, "%.1f km", safe / 1_000)
        }
        return if (estimated && safe > 0) "~$value" else value
    }

    fun distanceKm(
        kilometers: Double,
        estimated: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String = distance(kilometers * 1_000, estimated, locale)

    /** "45 min", "2 h", "2 h 15 min". Zero is a real number, never a dash. */
    fun duration(minutes: Long, locale: Locale = Locale.getDefault()): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0L -> String.format(locale, "%d min", rest)
            rest == 0L -> String.format(locale, "%d h", hours)
            else -> String.format(locale, "%d h %d min", hours, rest)
        }
    }

    /** "45m", "2h", "2h 15m" — for a stat tile a third of the screen wide. */
    fun durationCompact(minutes: Long, locale: Locale = Locale.getDefault()): String {
        val safe = minutes.coerceAtLeast(0)
        val hours = safe / 60
        val rest = safe % 60
        return when {
            hours == 0L -> String.format(locale, "%dm", rest)
            rest == 0L -> String.format(locale, "%dh", hours)
            else -> String.format(locale, "%dh %dm", hours, rest)
        }
    }

    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(DateTimeFormatter.ofPattern(CLOCK_PATTERN))

    fun clockRange(startMs: Long, endMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "${clock(startMs, zone)} – ${clock(endMs, zone)}"

    fun dayHeading(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern(DAY_HEADING_PATTERN, locale))

    fun dayHeadingWithYear(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern(DAY_HEADING_WITH_YEAR_PATTERN, locale))

    fun count(value: Int, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getIntegerInstance(locale).format(value)

    /**
     * How a day relates to today. The screen turns this into words so the wording lives in
     * string resources rather than in code.
     */
    sealed interface RelativeDay {
        data object Today : RelativeDay
        data object Yesterday : RelativeDay
        data class DaysAgo(val days: Int) : RelativeDay
        data class OnDate(val date: LocalDate) : RelativeDay
    }

    fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay {
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days == 0L -> RelativeDay.Today
            days == 1L -> RelativeDay.Yesterday
            days in 2..6 -> RelativeDay.DaysAgo(days.toInt())
            else -> RelativeDay.OnDate(date)
        }
    }
}
