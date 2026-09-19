package com.dailybeat.app.ui.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CalendarWeekTest {
    @Test
    fun `a Saturday is shown inside its Monday to Sunday calendar week`() {
        val days = mondayToSundayWeek(LocalDate.of(2026, 9, 19))

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 9, 14), days.first())
        assertEquals(LocalDate.of(2026, 9, 20), days.last())
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
    }

    @Test
    fun `Monday starts a new week rather than continuing a rolling window`() {
        val days = mondayToSundayWeek(LocalDate.of(2026, 9, 21))

        assertEquals(
            (21..27).map { LocalDate.of(2026, 9, it) },
            days,
        )
    }
}
