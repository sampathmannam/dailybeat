package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHealthTest {
    @Test
    fun `status distinguishes watching waiting healthy degraded and off`() {
        val now = 2_000_000L
        assertEquals(CaptureHealthLevel.OFF, CaptureHealth().status(now, enabled = false).level)
        assertEquals(
            CaptureHealthLevel.WATCHING,
            CaptureHealth().status(now, enabled = true, watcherArmed = true).level,
        )
        assertEquals(
            CaptureHealthLevel.WAITING,
            CaptureHealth(serviceRunning = true).status(now, enabled = true).level,
        )
        assertEquals(
            CaptureHealthLevel.HEALTHY,
            CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 60_000, lastAccuracyM = 20f).status(now, true).level,
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

    @Test fun `recent coarse point is approximate rather than healthy`() {
        val now = 2_000_000L
        val status = CaptureHealth(serviceRunning = true, lastStoredAtMs = now - 1000,
            lastAccuracyM = 200f, lastQuality = "approximate").status(now, true)
        assertEquals(CaptureHealthLevel.APPROXIMATE, status.level)
        assertEquals(200f, status.accuracyM)
        assertEquals(1000L, status.lastPointAgeMs)
    }

    @Test fun `missing or corrupt accuracy never claims a precise recent fix`() {
        for (accuracy in listOf(null, 0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            val status = CaptureHealth(serviceRunning = true, lastStoredAtMs = 1_999_000,
                lastAccuracyM = accuracy).status(2_000_000, true)
            assertEquals(CaptureHealthLevel.APPROXIMATE, status.level)
            assertEquals(null, status.accuracyM)
        }
    }

    @Test fun `stale imprecise point is a gap not a fresh approximate location`() {
        val status = CaptureHealth(serviceRunning = true, lastStoredAtMs = 1_000_000,
            lastAccuracyM = 200f).status(2_000_000, true)
        assertEquals(CaptureHealthLevel.DEGRADED, status.level)
    }

    @Test fun `future saved timestamp cannot appear fresh after clock changes`() {
        val status = CaptureHealth(serviceRunning = true, lastStoredAtMs = Long.MAX_VALUE,
            lastAccuracyM = 10f).status(2_000_000, true)
        assertEquals(CaptureHealthLevel.DEGRADED, status.level)
        assertEquals(null, status.lastPointAgeMs)
    }

    @Test fun `storage failure cannot appear healthy after a recent fix`() {
        val status = CaptureHealth(serviceRunning = true, lastStoredAtMs = 1_999_999,
            storageUnavailable = true).status(nowMs = 2_000_000, enabled = true)
        assertEquals(CaptureHealthLevel.DEGRADED, status.level)
        assertEquals(true, status.storageUnavailable)
    }

    @Test
    fun `disabled capture is off even when a stale watcher flag remains`() {
        assertEquals(
            CaptureHealthLevel.OFF,
            CaptureHealth().status(
                nowMs = 2_000_000L,
                enabled = false,
                watcherArmed = true,
            ).level,
        )
    }
}
