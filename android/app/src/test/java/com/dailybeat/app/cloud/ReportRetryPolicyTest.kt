package com.dailybeat.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRetryPolicyTest {

    @Test
    fun `only typed transient cloud errors retry`() {
        assertTrue(ReportRetryPolicy.shouldRetry(cloudFailure(429, retryable = true)))
        assertTrue(ReportRetryPolicy.shouldRetry(cloudFailure(503, retryable = true)))
        assertFalse(ReportRetryPolicy.shouldRetry(cloudFailure(401, retryable = false)))
        assertFalse(ReportRetryPolicy.shouldRetry(ReportIntegrityException("invalid report")))
        assertFalse(ReportRetryPolicy.shouldRetry(IllegalStateException("local failure")))
    }

    private fun cloudFailure(status: Int, retryable: Boolean) = CloudRequestException(
        provider = "Fake",
        statusCode = status,
        retryable = retryable,
        safeMessage = "Fake request failed.",
    )
}
