package com.dailybeat.app.patrolgrid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolEvidenceRetentionWorkerTest {
    @Test
    fun `expected expiry is a successful non-retryable retention run`() {
        val expectedExpiry = PatrolEvidenceRetentionReport(
            discardedActionCount = 1,
            discardedTrackPointCount = 2,
            discardedSnapshotCount = 0,
            pendingCloseCleared = true,
            evidenceOwnerCleared = true,
        )

        assertTrue(expectedExpiry.hasIncident)
        assertEquals(
            PatrolRetentionWorkOutcome.SUCCESS,
            patrolRetentionWorkOutcome(
                enforcementSucceeded = true,
                runAttemptCount = 0,
            ),
        )
    }

    @Test
    fun `true enforcement failure retries and eventually fails`() {
        assertEquals(
            PatrolRetentionWorkOutcome.RETRY,
            patrolRetentionWorkOutcome(
                enforcementSucceeded = false,
                runAttemptCount = PatrolEvidenceRetentionWorker.MAX_RETRIES - 1,
            ),
        )
        assertEquals(
            PatrolRetentionWorkOutcome.FAILURE,
            patrolRetentionWorkOutcome(
                enforcementSucceeded = false,
                runAttemptCount = PatrolEvidenceRetentionWorker.MAX_RETRIES,
            ),
        )
    }

    @Test
    fun `scheduler runs on device builds and skips Robolectric synthetic stores`() {
        assertTrue(shouldSchedulePatrolRetentionWorkers("google/panther/panther:16/release-keys"))
        assertFalse(shouldSchedulePatrolRetentionWorkers("robolectric"))
        assertEquals(24L, PatrolEvidenceRetentionWorker.PERIODIC_INTERVAL_HOURS)
    }
}
