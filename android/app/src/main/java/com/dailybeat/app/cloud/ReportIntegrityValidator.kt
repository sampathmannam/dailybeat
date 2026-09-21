package com.dailybeat.app.cloud

import com.dailybeat.app.util.InputPolicy

data class ReportIntegrityCheck(
    val isValid: Boolean,
    val violations: List<String>,
)

object ReportIntegrityValidator {
    private val citation = Regex("\\[([VE])(\\d+)]")

    fun validate(report: String, visitRefCount: Int, eventRefCount: Int): ReportIntegrityCheck {
        val violations = linkedSetOf<String>()
        if (report.isBlank()) violations += "Report is empty."
        // Validate numeric ranges directly. Allocating one string per reference lets malformed
        // source metadata exhaust memory before a provider answer can be rejected.
        val hasSources = visitRefCount > 0 || eventRefCount > 0
        fun allowed(match: MatchResult): Boolean {
            val digits = match.groupValues[2]
            val number = digits.toIntOrNull() ?: return false
            val limit = if (match.groupValues[1] == "V") visitRefCount else eventRefCount
            return number > 0 && number <= limit && digits == number.toString()
        }
        var hasValidCitation = false
        citation.findAll(report).forEach { match ->
            if (allowed(match)) hasValidCitation = true
            else if (violations.size < 20) {
                violations += "Unknown citation ${InputPolicy.bounded(match.value, 80)}."
            }
        }
        if (hasSources && !hasValidCitation) {
            violations += "Report contains no valid source citation."
        }
        // Citation coverage is structural, not proof that a cited source entails the sentence.
        if (hasSources && hasValidCitation) {
            val headings = setOf("overview", "timeline", "summary", "closing", "chronological narrative")
            val statements = report.split(Regex("(?<=[.!?।])\\s+(?!\\[)|[\\r\\n]+"))
            if (statements.any { statement ->
                val label = statement.trim().trim('#', '*', ':', ' ').lowercase()
                statement.any(Char::isLetter) && label !in headings &&
                    citation.findAll(statement).none(::allowed)
            }) violations += "Every factual sentence must include a source citation."
        }
        return ReportIntegrityCheck(violations.isEmpty(), violations.toList())
    }

    fun correctionPrompt(
        originalPrompt: String,
        invalidReport: String,
        violations: List<String>,
    ): String = buildString {
        appendLine(originalPrompt)
        appendLine()
        appendLine("CORRECTION REQUIRED:")
        violations.forEach { appendLine("- $it") }
        appendLine("Rewrite the report using only citation IDs present in DATA. Do not explain the correction.")
        appendLine("INVALID REPORT:")
        append(InputPolicy.bounded(invalidReport, 6_000))
    }
}
