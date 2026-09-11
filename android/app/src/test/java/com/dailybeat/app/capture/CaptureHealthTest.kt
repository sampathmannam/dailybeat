package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHealthTest {
    @Test
    fun `status distinguishes waiting healthy degraded and off`() {
        val now = 2_000_000L
        assertEquals(CaptureHealthLevel.OFF, CaptureHealth().status(now, enabled = false).level)
        assertEquals(
            CaptureHealthLevel.WAITING,
            CaptureHealth(serviceRunning = true).status(now, enabled = true).level,
        )
        assertEquals(
            CaptureHealthLevel.HEALTHY,
            CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 60_000).status(now, true).level,
        )
        assertEquals(
            CaptureHealthLevel.DEGRADED,
            CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 10 * 60_000).status(now, true).level,
        )
        // A very old point stays DEGRADED (calm), never escalates to an alarm.
        assertEquals(
            CaptureHealthLevel.DEGRADED,
            CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 30 * 60_000).status(now, true).level,
        )
    }
}
