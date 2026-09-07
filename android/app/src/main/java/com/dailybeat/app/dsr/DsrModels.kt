package com.dailybeat.app.dsr

enum class OperationalReportType {
    DSR,
    ALL_CRIME,
    TASMAC,
    FATAL_ACCIDENT,
    UNKNOWN,
}

enum class DsrPriority {
    CRITICAL,
    HIGH,
    MEDIUM,
    ROUTINE,
}

enum class DsrIssueSeverity {
    ERROR,
    WARNING,
    INFO,
}

enum class DsrMetricSemantics {
    DAILY,
    CUMULATIVE,
    PENDING,
    INVENTORY,
}

data class ParsedDsrCase(
    val caseKey: String,
    val stationCode: String,
    val stationName: String,
    val crimeNumber: String,
    val crimeYear: Int,
    val displayCrimeNumber: String,
    val head: String,
    val lawSections: String,
    val priority: DsrPriority,
    val needsReview: Boolean = false,
    val sourcePage: Int? = null,
)

data class ParsedStationSnapshot(
    val stationCode: String,
    val stationName: String,
    val reportedCases: Int?,
    val chargedCases: Int?,
    val otherDisposals: Int?,
    val eSummonsReceived: Int?,
    val eSummonsServed: Int?,
    val eSakshyaRecorded: Int?,
    val eSakshyaLinked: Int?,
    val mvDdCases: Int?,
    val mvOtherCases: Int?,
    val takenOnFile: Int?,
    val convictions: Int?,
    val acquittals: Int?,
)

data class ParsedDsrForecast(
    val stableKey: String,
    val eventDate: String,
    val stationCode: String,
    val stationName: String,
    val category: String,
    val priority: DsrPriority,
    val expectedCrowd: Int?,
    val details: String,
)

data class ParsedDsrMetric(
    val metricCode: String,
    val value: Int,
    val semantics: DsrMetricSemantics,
)

data class ParsedDsrIssue(
    val severity: DsrIssueSeverity,
    val code: String,
    val message: String,
    val caseKey: String? = null,
    val sourcePage: Int? = null,
)

data class ParsedOperationalReport(
    val reportType: OperationalReportType,
    val reportDate: String?,
    val pageCount: Int,
    val cases: List<ParsedDsrCase> = emptyList(),
    val stationSnapshots: List<ParsedStationSnapshot> = emptyList(),
    val forecasts: List<ParsedDsrForecast> = emptyList(),
    val metrics: List<ParsedDsrMetric> = emptyList(),
    val issues: List<ParsedDsrIssue> = emptyList(),
) {
    val qualityScore: Int
        get() {
            val deductions = issues.fold(0) { total, issue ->
                total + when (issue.severity) {
                    DsrIssueSeverity.ERROR -> 20
                    DsrIssueSeverity.WARNING -> 7
                    DsrIssueSeverity.INFO -> 1
                }
            }
            return (100 - deductions).coerceIn(0, 100)
        }
}

sealed interface DsrImportOutcome {
    data class Imported(
        val importId: String,
        val reportType: OperationalReportType,
        val reportDate: String?,
        val caseCount: Int,
        val issueCount: Int,
        val qualityScore: Int,
        val replacedPreviousVersion: Boolean,
    ) : DsrImportOutcome

    data class AlreadyImported(
        val importId: String,
        val originalFileName: String,
    ) : DsrImportOutcome
}
