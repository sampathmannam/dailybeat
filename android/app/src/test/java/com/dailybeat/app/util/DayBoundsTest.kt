package com.dailybeat.app.util

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayBoundsTest {

    @Test
    fun dayStartEnd_coversFullDay() {
        val date = LocalDate.of(2026, 8, 28)
        val (start, end) = DayBounds.dayStartEnd(date)
        assertTrue(end > start)
        assertTrue(end - start > 86_399_000L)
    }

    @Test
    fun todayStartEnd_isValidRange() {
        val (start, end) = DayBounds.todayStartEnd()
        assertTrue(end > start)
    }
}
