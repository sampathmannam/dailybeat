package com.dailybeat.app.ui.feed

import com.dailybeat.app.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

/**
 * One formatter per kind of number, used by every screen. Before this the same day read
 * "~4 km · 3h 34m" on Today and "4.0 km · 3 h 34 min" on Days.
 */
class FeedPresentationTest {

    @Test
    fun distanceIsMetresBelowAKilometreAndOneDecimalAbove() {
        assertEquals("0 m", Formatters.distanceKm(0.0))
        assertEquals("50 m", Formatters.distanceKm(0.05))
        assertEquals("450 m", Formatters.distanceKm(0.45))
        assertEquals("850 m", Formatters.distanceKm(0.85))
        assertEquals("1.0 km", Formatters.distanceKm(1.0))
        assertEquals("2.0 km", Formatters.distanceKm(2.0))
        assertEquals("12.4 km", Formatters.distanceKm(12.42))
    }

    @Test
    fun distanceRoundsRatherThanTruncates() {
        // 0.85 km is 849.999… m as a Double; truncation used to print 849 m.
        assertEquals("850 m", Formatters.distance(849.9999))
    }

    @Test
    fun estimatedDistanceIsMarkedEverywhereItIsNonZero() {
        assertEquals("~4.0 km", Formatters.distanceKm(4.0, estimated = true))
        assertEquals("~120 m", Formatters.distance(120.0, estimated = true))
        assertEquals("0 m", Formatters.distance(0.0, estimated = true))
    }

    @Test
    fun distanceFollowsTheDeviceLocale() {
        assertEquals("12,4 km", Formatters.distanceKm(12.42, locale = Locale.GERMANY))
        assertEquals("Sonntag, 6 September", Formatters.dayHeading(LocalDate.of(2026, 9, 6), Locale.GERMANY))
    }

    @Test
    fun durationForRowsIsSpacedAndNeverADash() {
        assertEquals("0 min", Formatters.duration(0))
        assertEquals("40 min", Formatters.duration(40))
        assertEquals("1 h", Formatters.duration(60))
        assertEquals("2 h", Formatters.duration(120))
        assertEquals("2 h 5 min", Formatters.duration(125))
        assertEquals("2 h 15 min", Formatters.duration(135))
    }

    @Test
    fun durationForStatTilesIsCompact() {
        assertEquals("0m", Formatters.durationCompact(0))
        assertEquals("45m", Formatters.durationCompact(45))
        assertEquals("1h", Formatters.durationCompact(60))
        assertEquals("2h 15m", Formatters.durationCompact(135))
        assertEquals("3h 34m", Formatters.durationCompact(214))
    }

    @Test
    fun negativeInputsClampToZeroInsteadOfPrintingGarbage() {
        assertEquals("0 m", Formatters.distance(-5.0))
        assertEquals("0 min", Formatters.duration(-3))
        assertEquals("0m", Formatters.durationCompact(-3))
    }

    @Test
    fun relativeDayClassifiesTheLastWeekAndFallsBackToTheDate() {
        val today = LocalDate.of(2026, 9, 6)

        assertEquals(Formatters.RelativeDay.Today, Formatters.relativeDay(today, today))
        assertEquals(Formatters.RelativeDay.Yesterday, Formatters.relativeDay(today.minusDays(1), today))
        assertEquals(Formatters.RelativeDay.DaysAgo(4), Formatters.relativeDay(today.minusDays(4), today))
        assertEquals(Formatters.RelativeDay.DaysAgo(6), Formatters.relativeDay(today.minusDays(6), today))
        assertEquals(
            Formatters.RelativeDay.OnDate(today.minusDays(7)),
            Formatters.relativeDay(today.minusDays(7), today),
        )
    }

    @Test
    fun countsUseGroupingForTheLocale() {
        assertEquals("4", Formatters.count(4, Locale.UK))
        assertEquals("1,250", Formatters.count(1_250, Locale.UK))
    }
}
