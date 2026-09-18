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
        val allowed = buildSet {
            (1..visitRefCount).forEach { add("[V$it]") }
            (1..eventRefCount).forEach { add("[E$it]") }
        }
        val cited = citation.findAll(report).map { it.value }.toList()
        cited.filterNot(allowed::contains).forEach { violations += "Unknown citation $it." }
        if (allowed.isNotEmpty() && cited.none(allowed::contains)) {
            violations += "Report contains no valid source citation."
        }
        // Citation coverage is structural, not proof that a cited source entails the sentence.
        if (allowed.isNotEmpty() && cited.any(allowed::contains)) {
            val headings = setOf("overview", "timeline", "summary", "closing", "chronological narrative")
            val statements = report.split(Regex("(?<=[.!?।])\\s+(?!\\[)|[\\r\\n]+"))
            if (statements.any { statement ->
                val label = statement.trim().trim('#', '*', ':', ' ').lowercase()
                statement.any(Char::isLetter) && label !in headings &&
                    citation.findAll(statement).none { it.value in allowed }
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
