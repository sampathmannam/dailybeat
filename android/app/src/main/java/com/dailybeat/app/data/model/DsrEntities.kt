package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata only. Source PDFs are copied into app-private storage and are never
 * included in DailyBeat's cloud backup snapshot.
 */
@Entity(
    tableName = "dsr_imports",
    indices = [
        Index(value = ["sha256"]),
        Index(value = ["reportDate", "reportType", "active"]),
    ],
)
data class DsrImport(
    @PrimaryKey val id: String,
    val sha256: String,
    val originalFileName: String,
    val storedFileName: String,
    val fileSizeBytes: Long,
    val reportType: String,
    val reportDate: String?,
    val importedAt: Long,
    val pageCount: Int,
    val caseCount: Int,
    val forecastCount: Int,
    val issueCount: Int,
    val qualityScore: Int,
    val active: Boolean = true,
    val replacedImportId: String? = null,
    @ColumnInfo(defaultValue = "1") val parserVersion: Int = 1,
    val reprocessedFromId: String? = null,
)

/** Immutable values for this parsing result; workflow records are deliberately separate. */
@Entity(tableName = "dsr_case_snapshots", indices = [Index("importId"), Index("caseKey")])
data class DsrCaseSnapshot(
    @PrimaryKey val id: String,
    val importId: String,
    @Embedded val caseData: DsrCase,
    val sourcePage: Int?,
    // Old versions kept only one mutable case row. Never imply reconstructed history is exact.
    val legacySnapshot: Boolean = false,
)

@Entity(tableName = "dsr_case_work", indices = [Index("dueDate")])
data class DsrCaseWork(
    @PrimaryKey val caseKey: String,
    val owner: String = "",
    val status: String = "OPEN",
    val nextAction: String = "",
    val dueDate: String? = null,
    val updatedAt: Long,
    val updatedBy: String,
    val revision: Long = 1,
)

@Entity(tableName = "dsr_procedure_checks", indices = [Index("caseKey")])
data class DsrProcedureCheck(
    @PrimaryKey val id: String,
    val caseKey: String,
    val code: String,
    val state: String,
    val note: String,
    val sourceImportId: String,
    val updatedAt: Long,
    val updatedBy: String,
    @ColumnInfo(defaultValue = "1") val guidanceVersion: Int = 1,
)

/** Append-only within the app; user-entered names are not authenticated signatures. */
@Entity(tableName = "dsr_case_audit", indices = [Index(value = ["caseKey", "createdAt"])])
data class DsrCaseAudit(
    @PrimaryKey val id: String,
    val caseKey: String,
    val createdAt: Long,
    val actor: String,
    val action: String,
    val detail: String,
)

/**
 * Deliberately excludes names, phone numbers, addresses and narrative gists.
 * The command dashboard only needs operational case metadata.
 */
@Entity(
    tableName = "dsr_cases",
    indices = [
        Index(value = ["stationCode"]),
        Index(value = ["lastSeenDate", "priority"]),
    ],
)
data class DsrCase(
    @PrimaryKey val caseKey: String,
    val stationCode: String,
    val stationName: String,
    val crimeNumber: String,
    val crimeYear: Int,
    val displayCrimeNumber: String,
    val head: String,
    val lawSections: String,
    val priority: String,
    val firstSeenDate: String,
    val lastSeenDate: String,
    val needsReview: Boolean,
)

@Entity(
    tableName = "dsr_case_mentions",
    indices = [Index(value = ["importId"]), Index(value = ["caseKey"])],
)
data class DsrCaseMention(
    @PrimaryKey val mentionKey: String,
    val importId: String,
    val caseKey: String,
    val reportDate: String,
    val sourcePage: Int? = null,
)

@Entity(
    tableName = "dsr_station_snapshots",
    indices = [
        Index(value = ["importId"]),
        Index(value = ["reportDate", "stationCode"]),
    ],
)
data class DsrStationSnapshot(
    @PrimaryKey val id: String,
    val importId: String,
    val reportDate: String,
    val stationCode: String,
    val stationName: String,
    val reportedCases: Int? = null,
    val chargedCases: Int? = null,
    val otherDisposals: Int? = null,
    val eSummonsReceived: Int? = null,
    val eSummonsServed: Int? = null,
    val eSakshyaRecorded: Int? = null,
    val eSakshyaLinked: Int? = null,
    val mvDdCases: Int? = null,
    val mvOtherCases: Int? = null,
    val takenOnFile: Int? = null,
    val convictions: Int? = null,
    val acquittals: Int? = null,
)

@Entity(
    tableName = "dsr_metric_snapshots",
    indices = [
        Index(value = ["importId"]),
        Index(value = ["reportType", "metricCode", "reportDate"]),
    ],
)
data class DsrMetricSnapshot(
    @PrimaryKey val id: String,
    val importId: String,
    val reportDate: String?,
    val reportType: String,
    val metricCode: String,
    val metricValue: Int,
    val semantics: String,
)

@Entity(
    tableName = "dsr_forecasts",
    indices = [Index(value = ["importId"]), Index(value = ["eventDate", "priority"])],
)
data class DsrForecast(
    @PrimaryKey val id: String,
    val importId: String,
    val reportDate: String,
    val eventDate: String,
    val stationCode: String,
    val stationName: String,
    val category: String,
    val priority: String,
    val expectedCrowd: Int? = null,
    val details: String,
)

@Entity(
    tableName = "dsr_quality_issues",
    indices = [Index(value = ["importId"])],
)
data class DsrQualityIssue(
    @PrimaryKey val id: String,
    val importId: String,
    val severity: String,
    val code: String,
    val message: String,
    val caseKey: String? = null,
    val sourcePage: Int? = null,
)
