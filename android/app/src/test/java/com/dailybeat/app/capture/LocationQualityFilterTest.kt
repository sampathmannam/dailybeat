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

    private fun sample(
        lat: Double = 11.4557,
        lon: Double = 78.1856,
        timestamp: Long = 1_000L,
        accuracy: Float = 20f,
        mock: Boolean = false,
    ) = LocationSample(lat, lon, timestamp, accuracy, mock)
}
