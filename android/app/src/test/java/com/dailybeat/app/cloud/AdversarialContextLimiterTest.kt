package com.dailybeat.app.cloud

import org.junit.Assert.assertTrue
import org.junit.Test

class AdversarialContextLimiterTest {

    @Test
    fun trimForLlm_truncatesHugeContext() {
        val huge = "A".repeat(20_000)
        val trimmed = ContextLimiter.trimForLlm(huge)
        assertTrue(trimmed.length < huge.length)
        assertTrue(trimmed.contains("TRUNCATED"))
    }

    @Test
    fun trimForLlm_keepsSmallContext() {
        val small = "Officer visited HQ at 10:00."
        assertTrue(ContextLimiter.trimForLlm(small) == small)
    }
}
