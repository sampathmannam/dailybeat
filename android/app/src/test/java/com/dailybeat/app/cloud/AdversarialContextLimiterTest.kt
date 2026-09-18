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

    @Test
    fun trimForLlm_never_splits_a_unicode_code_point() {
        val huge = "A".repeat(13_799) + "🙂".repeat(1_000)

        val trimmed = ContextLimiter.trimForLlm(huge)

        trimmed.forEachIndexed { index, char ->
            if (Character.isHighSurrogate(char)) {
                assertTrue(index + 1 < trimmed.length && Character.isLowSurrogate(trimmed[index + 1]))
            }
            if (Character.isLowSurrogate(char)) {
                assertTrue(index > 0 && Character.isHighSurrogate(trimmed[index - 1]))
            }
        }
    }
}
