package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A one-hour privacy pause used to surface on Today as "Capture is off — DailyBeat is not
 * recording your route", in the error colour. These tests pin the distinction: a chosen,
 * self-reversing pause is never reported as a failure, and a real failure is never excused as one.
 */
class CapturePausedStatusTest {

    private val now = 2_000_000L
    private val running = CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 60_000)

    @Test
    fun `a live pause reports PAUSED and carries the resume time`() {
        val resumeAt = now + 30 * 60_000L
        // Capture is stopped while paused, which is exactly why serviceRunning is false here.
        val status = CaptureHealth(serviceRunning = false)
            .status(now, enabled = true, pausedUntilMs = resumeAt)

        assertEquals(CaptureHealthLevel.PAUSED, status.level)
        assertEquals(resumeAt, status.resumesAtMs)
    }

    @Test
    fun `a pause is never reported as OFF`() {
        val status = CaptureHealth(serviceRunning = false)
            .status(now, enabled = true, pausedUntilMs = now + 60_000L)

        assertNotEquals(CaptureHealthLevel.OFF, status.level)
    }

    @Test
    fun `an expired deadline falls through to the real underlying state`() {
        // One tick after the hour is up the card must go back to describing capture itself,
        // without anyone having to clear the deadline first.
        val status = running.status(now, enabled = true, pausedUntilMs = now - 1)

        assertEquals(CaptureHealthLevel.HEALTHY, status.level)
        assertNull(status.resumesAtMs)
    }

    @Test
    fun `a deadline exactly at now has already elapsed`() {
        assertEquals(
            CaptureHealthLevel.HEALTHY,
            running.status(now, enabled = true, pausedUntilMs = now).level,
        )
    }

    @Test
    fun `an expired deadline over a stopped service is OFF, not PAUSED`() {
        // The pause ended but capture did not come back. That is a genuine problem and must not be
        // softened into "paused, resuming shortly".
        val status = CaptureHealth(serviceRunning = false)
            .status(now, enabled = true, pausedUntilMs = now - 60_000L)

        assertEquals(CaptureHealthLevel.OFF, status.level)
    }

    @Test
    fun `switching GPS off wins over a stale pause deadline`() {
        // Disabling capture entirely is OFF even if a pause deadline is still sitting on disk,
        // otherwise the officer would be told capture resumes by itself when it never will.
        val status = CaptureHealth(serviceRunning = false)
            .status(now, enabled = false, pausedUntilMs = now + 60 * 60_000L)

        assertEquals(CaptureHealthLevel.OFF, status.level)
        assertNull(status.resumesAtMs)
    }

    @Test
    fun `no pause argument leaves every existing state untouched`() {
        assertEquals(CaptureHealthLevel.HEALTHY, running.status(now, enabled = true).level)
        assertEquals(CaptureHealthLevel.OFF, CaptureHealth().status(now, enabled = false).level)
        assertEquals(
            CaptureHealthLevel.WAITING,
            CaptureHealth(serviceRunning = true).status(now, enabled = true).level,
        )
        assertEquals(
            CaptureHealthLevel.DEGRADED,
            CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 10 * 60_000)
                .status(now, enabled = true).level,
        )
    }
}
