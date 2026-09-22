package com.dailybeat.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitTrackerStateTest {
    private val start = 1_757_000_000_000L

    @Test
    fun `legacy transit checkpoint without an arrival candidate remains readable`() {
        assertTrue(transit().isUsable(nowMs = start + 600_000L))
    }

    @Test
    fun `transit checkpoint can retain an observed but unconfirmed arrival`() {
        assertTrue(candidate().isUsable(nowMs = start + 600_000L))
    }

    @Test
    fun `arrival candidate cannot predate travel or exceed its latest observation`() {
        assertFalse(candidate().copy(dwellStartMs = start - 1L).isUsable(nowMs = start + 600_000L))
        assertFalse(candidate().copy(dwellStartMs = start + 600_001L).isUsable(nowMs = start + 600_000L))
    }

    @Test
    fun `candidate coordinates must remain a complete finite pair`() {
        assertFalse(candidate().copy(dwellLon = null).isUsable(nowMs = start + 600_000L))
        assertFalse(candidate().copy(dwellLat = Double.NaN).isUsable(nowMs = start + 600_000L))
    }

    @Test
    fun `transit displacement must be finite nonnegative and geographically possible`() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 21_000_000.0)) {
            assertFalse(candidate().copy(maxTransitDisplacementM = invalid).isUsable(nowMs = start + 600_000L))
        }
        assertTrue(candidate().copy(maxTransitDisplacementM = 2000.0).isUsable(nowMs = start + 600_000L))
        assertFalse(candidate().copy(inTransit = false, maxTransitDisplacementM = 2000.0).isUsable(nowMs = start + 600_000L))
    }

    private fun candidate() = transit().copy(dwellLat = 11.4657, dwellLon = 78.1856, dwellStartMs = start + 300_000L)

    private fun transit() = VisitTrackerState(
        dwellLat = null,
        dwellLon = null,
        dwellStartMs = 0L,
        lastSampleMs = start + 600_000L,
        transitStartMs = start,
        transitLat = 11.4657,
        transitLon = 78.1856,
        departureLat = 11.4557,
        departureLon = 78.1856,
        inTransit = true,
    )
}
