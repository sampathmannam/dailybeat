package com.dailybeat.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportIntegrityValidatorTest {

    @Test
    fun rejectsVisitReferencesWhenSourceHasNoVisits() {
        val result = ReportIntegrityValidator.validate(
            "Only one event occurred [E1]. No visits occurred [V1][V2].",
            visitRefCount = 0,
            eventRefCount = 1,
        )

        assertFalse(result.isValid)
        assertEquals(listOf("Unknown citation [V1].", "Unknown citation [V2]."), result.violations)
    }

    @Test
    fun acceptsOnlyReferencesPresentInTheSourceContext() {
        val result = ReportIntegrityValidator.validate(
            "Visited headquarters [V1]. Recorded a briefing [E1].",
            visitRefCount = 1,
            eventRefCount = 1,
        )

        assertTrue(result.isValid)
    }

    @Test
    fun rejectsNarrativeWithoutAnySourceCitationWhenSourcesExist() {
        val result = ReportIntegrityValidator.validate("A briefing occurred.", 0, 1)

        assertFalse(result.isValid)
        assertTrue(result.violations.contains("Report contains no valid source citation."))
    }

    @Test
    fun rejectsEmptyOutput() {
        val result = ReportIntegrityValidator.validate(" \n\t", 0, 0)

        assertFalse(result.isValid)
        assertEquals(listOf("Report is empty."), result.violations)
    }

    @Test
    fun acceptsDuplicateValidReferences() {
        val result = ReportIntegrityValidator.validate(
            "Visited headquarters [V1], then returned to headquarters [V1].",
            visitRefCount = 1,
            eventRefCount = 0,
        )

        assertTrue(result.isValid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun acceptsNarrativeWhenNoSourcesExist() {
        val result = ReportIntegrityValidator.validate("No activity was recorded.", 0, 0)

        assertTrue(result.isValid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun rejectsVisitReferenceZero() {
        val result = ReportIntegrityValidator.validate("Visited headquarters [V0].", 1, 0)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                "Unknown citation [V0].",
                "Report contains no valid source citation.",
            ),
            result.violations,
        )
    }

    @Test
    fun rejectsEventReferenceBeyondAvailableRange() {
        val result = ReportIntegrityValidator.validate("A briefing occurred [E2].", 0, 1)

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                "Unknown citation [E2].",
                "Report contains no valid source citation.",
            ),
            result.violations,
        )
    }

    @Test
    fun ignoresLowercaseAndNonReferenceBrackets() {
        val result = ReportIntegrityValidator.validate(
            "Notes [v1], [e1], [X1], [Vone], and [E1x].",
            visitRefCount = 0,
            eventRefCount = 0,
        )

        assertTrue(result.isValid)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun preservesDeterministicViolationOrderWithoutDuplicates() {
        val result = ReportIntegrityValidator.validate(
            "Invalid references [E3], [V2], [E3], and [V0].",
            visitRefCount = 1,
            eventRefCount = 1,
        )

        assertFalse(result.isValid)
        assertEquals(
            listOf(
                "Unknown citation [E3].",
                "Unknown citation [V2].",
                "Unknown citation [V0].",
                "Report contains no valid source citation.",
            ),
            result.violations,
        )
    }

    @Test
    fun buildsDeterministicCorrectionPrompt() {
        val result = ReportIntegrityValidator.correctionPrompt(
            originalPrompt = "ORIGINAL PROMPT",
            invalidReport = "Bad reference [V1].",
            violations = listOf("Unknown citation [V1]."),
        )

        assertEquals(
            """
            ORIGINAL PROMPT

            CORRECTION REQUIRED:
            - Unknown citation [V1].
            Rewrite the report using only citation IDs present in DATA. Do not explain the correction.
            INVALID REPORT:
            Bad reference [V1].
            """.trimIndent(),
            result,
        )
    }

    @Test
    fun limitsInvalidReportInCorrectionPromptToSixThousandCharacters() {
        val result = ReportIntegrityValidator.correctionPrompt(
            originalPrompt = "ORIGINAL PROMPT",
            invalidReport = "x".repeat(6_001),
            violations = emptyList(),
        )

        assertEquals(6_000, result.substringAfter("INVALID REPORT:\n").length)
    }
}
