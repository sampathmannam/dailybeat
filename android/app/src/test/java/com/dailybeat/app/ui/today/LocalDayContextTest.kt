package com.dailybeat.app.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalDayContextTest {

    @Test
    fun `midnight changes the active day for a long lived Today screen`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val beforeMidnight = Instant.parse("2026-09-18T18:29:59Z").toEpochMilli()
        val afterMidnight = Instant.parse("2026-09-18T18:30:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 9, 18), localDayContext(beforeMidnight, zone).date)
        assertEquals(LocalDate.of(2026, 9, 19), localDayContext(afterMidnight, zone).date)
    }

    @Test
    fun `time zone is part of the subscription key even when date is unchanged`() {
        val instant = Instant.parse("2026-09-19T12:00:00Z").toEpochMilli()
        val kolkata = localDayContext(instant, ZoneId.of("Asia/Kolkata"))
        val singapore = localDayContext(instant, ZoneId.of("Asia/Singapore"))

        assertEquals(kolkata.date, singapore.date)
        assertNotEquals(kolkata, singapore)
    }
}
