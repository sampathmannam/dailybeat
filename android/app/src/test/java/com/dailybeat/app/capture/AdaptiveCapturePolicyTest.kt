package com.dailybeat.app.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveCapturePolicyTest {
    @Test
    fun `stillness requires a full confirmation window while capture remains enabled`() {
        val stillSince = 10_000L

        assertFalse(
            AdaptiveCapturePolicy.shouldStopForStillness(
                stillSince,
                stillSince + AdaptiveCapturePolicy.STILL_CONFIRMATION_MS - 1,
                captureEnabled = true,
            ),
        )
        assertTrue(
            AdaptiveCapturePolicy.shouldStopForStillness(
                stillSince,
                stillSince + AdaptiveCapturePolicy.STILL_CONFIRMATION_MS,
                captureEnabled = true,
            ),
        )
        assertFalse(
            AdaptiveCapturePolicy.shouldStopForStillness(
                stillSince,
                stillSince + AdaptiveCapturePolicy.STILL_CONFIRMATION_MS,
                captureEnabled = false,
            ),
        )
    }

    @Test
    fun `idle safety net is available only when a movement watcher is armed`() {
        val lastMovement = 25_000L
        val deadline = lastMovement + AdaptiveCapturePolicy.LOCATION_IDLE_TIMEOUT_MS

        assertFalse(
            AdaptiveCapturePolicy.shouldStopForLocationIdle(
                lastMovement,
                deadline,
                hasMotionWatcher = false,
            ),
        )
        assertFalse(
            AdaptiveCapturePolicy.shouldStopForLocationIdle(
                lastMovement,
                deadline - 1,
                hasMotionWatcher = true,
            ),
        )
        assertTrue(
            AdaptiveCapturePolicy.shouldStopForLocationIdle(
                lastMovement,
                deadline,
                hasMotionWatcher = true,
            ),
        )
    }

    @Test
    fun `settling request is deliberately quieter than the moving request`() {
        assertTrue(ActiveCaptureProfile.SETTLING.intervalMs > ActiveCaptureProfile.MOVING.intervalMs)
        assertTrue(ActiveCaptureProfile.SETTLING.maxDelayMs > ActiveCaptureProfile.MOVING.maxDelayMs)
        assertTrue(ActiveCaptureProfile.SETTLING.minDistanceM > ActiveCaptureProfile.MOVING.minDistanceM)
    }
}
