package com.dailybeat.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Classifies how stale the last stored GPS point is. The capture card leads with this because
 * accuracy answers "how precise", not "how current".
 */
class FixAgeTest {

    @Test
    fun `under a minute is just now, not zero minutes`() {
        // "Last reliable fix 0 min ago" is both ugly and slightly alarming.
        assertEquals(Formatters.FixAge.JustNow, Formatters.fixAge(0))
        assertEquals(Formatters.FixAge.JustNow, Formatters.fixAge(59_999))
    }

    @Test
    fun `a whole minute rounds down to minutes, never up`() {
        // Never claim the fix is fresher than it is.
        assertEquals(Formatters.FixAge.Ago(1), Formatters.fixAge(60_000))
        assertEquals(Formatters.FixAge.Ago(1), Formatters.fixAge(119_999))
        assertEquals(Formatters.FixAge.Ago(2), Formatters.fixAge(120_000))
    }

    @Test
    fun `the degraded threshold reads as five minutes`() {
        // CaptureHealthStore.HEALTHY_AGE_MS is 5 minutes; one ms past it is where DEGRADED begins.
        assertEquals(Formatters.FixAge.Ago(5), Formatters.fixAge(5 * 60_000))
        assertEquals(Formatters.FixAge.Ago(5), Formatters.fixAge(5 * 60_000 + 1))
    }

    @Test
    fun `hours are carried through for the duration formatter to render`() {
        assertEquals(Formatters.FixAge.Ago(130), Formatters.fixAge(130 * 60_000))
        assertEquals("2 h 10 min", Formatters.duration(130))
    }

    @Test
    fun `a negative age from a clock change is clamped, never rendered as a negative`() {
        assertEquals(Formatters.FixAge.JustNow, Formatters.fixAge(-5_000))
    }
}
