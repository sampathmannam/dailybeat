package com.dailybeat.app.cloud

object ReportRetryPolicy {
    fun shouldRetry(error: Throwable): Boolean =
        (error as? CloudRequestException)?.retryable == true
}
