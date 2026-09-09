package com.dailybeat.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportIntegrityValidatorTest {

    @Test
    fun `rejects references absent from source`() {
        val result = ReportIntegrityValidator.validate(
            "Recorded a note [E1] and an invented visit [V1].",
            visitRefCount = 0,
            eventRefCount = 1,
        )

        assertFalse(result.isValid)
        assertEquals(listOf("Unknown citation [V1]."), result.violations)
    }

    @Test
    fun `requires at least one real citation`() {
        val result = ReportIntegrityValidator.validate("A briefing occurred.", 0, 1)

        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Report contains no valid source citation."))
    }

    @Test
    fun `accepts references present in source`() {
        val result = ReportIntegrityValidator.validate(
            "Visited headquarters [V1]. Recorded a briefing [E1].",
            visitRefCount = 1,
            eventRefCount = 1,
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `rejects empty output`() {
        val result = ReportIntegrityValidator.validate(" \n\t", 0, 0)

        assertFalse(result.isValid)
        assertEquals(listOf("Report is empty."), result.violations)
    }
}
