package com.dailybeat.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolLocationPolicyTest {
    private val nowMs = 1_800_000L

    @Test
    fun `fresh finite GPS fix within backend limits is accepted`() {
        assertTrue(
            isPatrolLocationUsable(
                latitude = 12.9716,
                longitude = 77.5946,
                accuracyM = 18f,
                timestampMs = nowMs,
                nowMs = nowMs,
            ),
        )
    }

    @Test
    fun `fix too coarse for evidence ingestion is rejected`() {
        assertFalse(usable(accuracyM = 5_000.1f))
    }

    @Test
    fun `non finite and out of range coordinates are rejected`() {
        assertFalse(usable(latitude = Double.NaN))
        assertFalse(usable(latitude = 90.1))
        assertFalse(usable(longitude = Double.POSITIVE_INFINITY))
        assertFalse(usable(longitude = -180.1))
    }

    @Test
    fun `invalid accuracy is rejected`() {
        assertFalse(usable(accuracyM = Float.NaN))
        assertFalse(usable(accuracyM = -0.1f))
    }

    @Test
    fun `stale and future fixes outside server clock tolerance are rejected`() {
        assertFalse(usable(timestampMs = nowMs - 300_001L))
        assertFalse(usable(timestampMs = nowMs + 300_001L))
    }

    @Test
    fun `backend boundary values remain accepted`() {
        assertTrue(usable(accuracyM = 5_000f, timestampMs = nowMs - 300_000L))
        assertTrue(usable(latitude = -90.0, longitude = 180.0, timestampMs = nowMs + 300_000L))
    }

    private fun usable(
        latitude: Double = 12.9716,
        longitude: Double = 77.5946,
        accuracyM: Float = 18f,
        timestampMs: Long = nowMs,
    ): Boolean = isPatrolLocationUsable(
        latitude = latitude,
        longitude = longitude,
        accuracyM = accuracyM,
        timestampMs = timestampMs,
        nowMs = nowMs,
    )
}
