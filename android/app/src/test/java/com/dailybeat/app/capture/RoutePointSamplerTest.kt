package com.dailybeat.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePointSamplerTest {
    @Test
    fun `first point and a route point after five minutes are always retained`() {
        val first = sample(timestamp = 1_000L)
        val aged = sample(timestamp = first.timestampMs + 5 * 60 * 1_000L)

        assertTrue(RoutePointSampler.shouldPersist(first, previousPersisted = null))
        assertTrue(RoutePointSampler.shouldPersist(aged, first))
    }

    @Test
    fun `small jitter is not written as a map vertex`() {
        val first = sample()
        val jitter = sample(lat = 12.97165, lon = 77.59465, timestamp = 60_000L)

        assertFalse(RoutePointSampler.shouldPersist(jitter, first))
    }

    @Test
    fun `meaningful travel and sharply better accuracy remain route evidence`() {
        val first = sample(accuracy = 90f)
        val travelled = sample(lat = 12.97255, lon = 77.59465, timestamp = 60_000L)
        val recoveredAccuracy = sample(accuracy = 30f, timestamp = 60_000L)

        assertTrue(RoutePointSampler.shouldPersist(travelled, first))
        assertTrue(RoutePointSampler.shouldPersist(recoveredAccuracy, first))
    }

    private fun sample(
        lat: Double = 12.97160,
        lon: Double = 77.59460,
        timestamp: Long = 1_000L,
        accuracy: Float = 20f,
    ) = LocationSample(lat, lon, timestamp, accuracy)
}
