package com.dailybeat.app.capture

import com.dailybeat.app.data.db.RecordedFixTiming
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CaptureGapAuditTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun point(value: String) = RecordedFixTiming(time(value), 20f, "good")
    private val now = time("2026-10-06T18:00:00Z")

    @Test fun `ten minute boundary is continuous and larger gaps are reported`() {
        val days = CaptureGapAudit.days(listOf(point("2026-10-05T05:00:00Z"),
            point("2026-10-05T05:10:00Z"), point("2026-10-05T05:21:00Z")), now, zone)
        assertEquals(1, days.size)
        assertEquals(1, days.single().gaps.size)
        assertEquals(660_000L, days.single().gaps.single().durationMs)
    }

    @Test fun `gap across midnight is apportioned to each local day`() {
        val days = CaptureGapAudit.days(listOf(point("2026-10-04T18:20:00Z"),
            point("2026-10-04T18:50:00Z")), now, zone)
        assertEquals(listOf("2026-10-04", "2026-10-05"), days.map { it.date.toString() })
        assertEquals(listOf(600_000L, 1_200_000L), days.map { it.gaps.single().durationMs })
    }

    @Test fun `fully unrecorded days between observations remain visible`() {
        val days = CaptureGapAudit.days(listOf(point("2026-10-03T17:30:00Z"),
            point("2026-10-05T19:30:00Z")), now, zone)
        assertEquals(listOf(1, 0, 0, 1), days.map { it.pointCount })
        assertEquals(24 * 60 * 60_000L, days[1].gaps.single().durationMs)
    }

    @Test fun `audit sorts duplicates and excludes invalid future timestamps without altering input`() {
        val old = point("2026-10-05T05:00:00Z")
        val newer = point("2026-10-05T05:20:00Z")
        val input = listOf(newer, old, old, RecordedFixTiming(-1, 0f, "bad"),
            point("2027-01-01T00:00:00Z"))
        val day = CaptureGapAudit.days(input, now, zone).single()
        assertEquals(3, day.pointCount)
        assertEquals(1, day.gaps.size)
        assertEquals(5, input.size)
    }

    @Test fun `a single point cannot establish either coverage or an outage`() {
        assertTrue(CaptureGapAudit.days(emptyList(), now, zone).isEmpty())
        val day = CaptureGapAudit.days(listOf(point("2026-10-05T05:00:00Z")), now, zone).single()
        assertTrue(day.gaps.isEmpty())
    }

    @Test fun `daylight saving boundary uses actual local day duration`() {
        val days = CaptureGapAudit.days(listOf(point("2026-03-07T23:00:00Z"),
            point("2026-03-09T05:00:00Z")), now, ZoneId.of("America/New_York"))
        assertEquals(23 * 60 * 60_000L, days.single { it.date.toString() == "2026-03-08" }.gaps.single().durationMs)
    }
}
