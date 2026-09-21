package com.dailybeat.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationQualityFilterTest {
    @Test
    fun `accepts accurate real fixes and classifies weaker fixes`() {
        val good = LocationQualityFilter.assess(sample(accuracy = 18f), previous = null)
        val approximate = LocationQualityFilter.assess(sample(accuracy = 120f), previous = null)

        assertTrue(good.accepted)
        assertEquals("good", good.quality)
        assertTrue(approximate.accepted)
        assertEquals("approximate", approximate.quality)
    }

    @Test
    fun `rejects privacy and route-corrupting fixes`() {
        assertEquals("invalid-coordinate", LocationQualityFilter.assess(sample(lat = 0.0, lon = 0.0), null).reason)
        assertEquals("mock-location", LocationQualityFilter.assess(sample(mock = true), null).reason)
        assertEquals("weak-accuracy", LocationQualityFilter.assess(sample(accuracy = 400f), null).reason)

        val previous = sample(lat = 11.4557, lon = 78.1856, timestamp = 1_000L)
        val impossible = sample(lat = 13.0827, lon = 80.2707, timestamp = 61_000L)
        val decision = LocationQualityFilter.assess(impossible, previous)
        assertFalse(decision.accepted)
        assertEquals("implausible-jump", decision.reason)
    }

    @Test
    fun `rejects duplicate and out of order fixes without rolling the route backward`() {
        val previous = sample(timestamp = 10_000L)

        assertEquals(
            "stale-time",
            LocationQualityFilter.assess(sample(timestamp = 10_000L), previous).reason,
        )
        assertEquals(
            "stale-time",
            LocationQualityFilter.assess(sample(timestamp = 9_999L), previous).reason,
        )
    }

    @Test
    fun `future timestamps cannot poison route ordering but durable historical fixes still replay`() {
        val now = 1_757_000_000_000L
        assertEquals("future-time", LocationQualityFilter.assess(sample(timestamp = Long.MAX_VALUE), null, now).reason)
        assertEquals("future-time", LocationQualityFilter.assess(
            sample(timestamp = now + LocationQualityFilter.MAX_FUTURE_SKEW_MS + 1), null, now,
        ).reason)
        assertTrue(LocationQualityFilter.assess(
            sample(timestamp = now + LocationQualityFilter.MAX_FUTURE_SKEW_MS), null, now,
        ).accepted)
        assertTrue(LocationQualityFilter.assess(sample(timestamp = now - 7 * 86_400_000L), null, now).accepted)
    }

    @Test
    fun `antipodal rounding cannot bypass the speed limit through NaN`() {
        val previous = sample(lat = 2.5, lon = 78.1856, timestamp = 1_000L)
        val opposite = sample(lat = -2.5, lon = -101.8144, timestamp = 2_000L)
        assertEquals("implausible-jump", LocationQualityFilter.assess(opposite, previous).reason)
    }

    @Test
    fun `nonfinite derived speed fails closed`() {
        val corruptPrevious = sample(lat = Double.NaN, timestamp = 1_000L)
        assertEquals("implausible-jump", LocationQualityFilter.assess(sample(timestamp = 2_000L), corruptPrevious).reason)
    }

    private fun sample(
        lat: Double = 11.4557,
        lon: Double = 78.1856,
        timestamp: Long = 1_000L,
        accuracy: Float = 20f,
        mock: Boolean = false,
    ) = LocationSample(lat, lon, timestamp, accuracy, mock)
}
