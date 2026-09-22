package com.dailybeat.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePointSamplerTest {
    @Test
    fun `antipodal route distance remains finite`() {
        val first = sample(lat = 2.5, lon = 78.1856)
        val opposite = sample(lat = -2.5, lon = -101.8144)
        assertEquals(Math.PI * 6_371_000.0, RoutePointSampler.distanceM(first, opposite), 0.1)
    }

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

    @Test
    fun `overlapping accuracy circles do not turn a stationary shift into travel`() {
        val first = sample(accuracy = 80f)
        val uncertainShift = sample(lat = 12.97260, timestamp = 60_000L, accuracy = 80f)
        val clearMovement = sample(lat = 12.97410, timestamp = 60_000L, accuracy = 80f)

        assertFalse(RoutePointSampler.shouldPersist(uncertainShift, first))
        assertTrue(RoutePointSampler.shouldPersist(clearMovement, first))
    }

    @Test
    fun `uncertain stationary fixes retain five minute coverage and material accuracy recovery`() {
        val first = sample(accuracy = 200f)
        val heartbeat = sample(lat = 12.97260, timestamp = 301_000L, accuracy = 200f)
        val recovered = sample(lat = 12.97260, timestamp = 60_000L, accuracy = 20f)

        assertTrue(RoutePointSampler.shouldPersist(heartbeat, first))
        assertTrue(RoutePointSampler.shouldPersist(recovered, first))
    }

    @Test
    fun `stale improved fixes cannot regress the route checkpoint`() {
        val first = sample(accuracy = 200f)
        assertFalse(RoutePointSampler.shouldPersist(sample(timestamp = first.timestampMs, accuracy = 20f), first))
        assertFalse(RoutePointSampler.shouldPersist(sample(timestamp = first.timestampMs - 1L, accuracy = 20f), first))
    }

    private fun sample(
        lat: Double = 12.97160,
        lon: Double = 77.59460,
        timestamp: Long = 1_000L,
        accuracy: Float = 20f,
    ) = LocationSample(lat, lon, timestamp, accuracy)
}
