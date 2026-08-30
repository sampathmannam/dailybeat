package com.dailybeat.app.cloud

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportRetryPolicyTest {

    @Test
    fun retriesOnlyTypedRetryableCloudFailures() {
        assertTrue(ReportRetryPolicy.shouldRetry(cloudFailure(null, retryable = true)))
        assertTrue(ReportRetryPolicy.shouldRetry(cloudFailure(429, retryable = true)))
        assertTrue(ReportRetryPolicy.shouldRetry(cloudFailure(500, retryable = true)))
    }

    @Test
    fun doesNotRetryTypedNonretryableCloudFailures() {
        assertFalse(ReportRetryPolicy.shouldRetry(cloudFailure(401, retryable = false)))
        assertFalse(ReportRetryPolicy.shouldRetry(cloudFailure(400, retryable = false)))
    }

    @Test
    fun doesNotRetryIntegrityOrUntypedFailures() {
        assertFalse(ReportRetryPolicy.shouldRetry(ReportIntegrityException("invalid report")))
        assertFalse(ReportRetryPolicy.shouldRetry(IOException("untyped network failure")))
        assertFalse(ReportRetryPolicy.shouldRetry(IllegalStateException("local failure")))
    }

    private fun cloudFailure(statusCode: Int?, retryable: Boolean) = CloudRequestException(
        provider = "Fake",
        statusCode = statusCode,
        retryable = retryable,
        safeMessage = "Fake request failed.",
    )
}
