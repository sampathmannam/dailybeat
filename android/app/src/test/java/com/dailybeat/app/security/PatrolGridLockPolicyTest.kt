package com.dailybeat.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolGridLockPolicyTest {

    @Test
    fun `explicit lock always protects an existing session`() {
        assertTrue(
            shouldLockPatrolGrid(
                hasSession = true,
                explicitlyLocked = true,
                backgroundedAtMs = null,
                nowMs = 1_000L,
            ),
        )
    }

    @Test
    fun `background lock activates at timeout but not before`() {
        assertFalse(
            shouldLockPatrolGrid(
                hasSession = true,
                explicitlyLocked = false,
                backgroundedAtMs = 1_000L,
                nowMs = 1_000L + PATROLGRID_BACKGROUND_LOCK_TIMEOUT_MS - 1L,
            ),
        )
        assertTrue(
            shouldLockPatrolGrid(
                hasSession = true,
                explicitlyLocked = false,
                backgroundedAtMs = 1_000L,
                nowMs = 1_000L + PATROLGRID_BACKGROUND_LOCK_TIMEOUT_MS,
            ),
        )
    }

    @Test
    fun `clock rollback fails closed while signed in`() {
        assertTrue(
            shouldLockPatrolGrid(
                hasSession = true,
                explicitlyLocked = false,
                backgroundedAtMs = 10_000L,
                nowMs = 9_999L,
            ),
        )
        assertFalse(
            shouldLockPatrolGrid(
                hasSession = false,
                explicitlyLocked = true,
                backgroundedAtMs = 10_000L,
                nowMs = 9_999L,
            ),
        )
    }
}
