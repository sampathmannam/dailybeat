package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata only. Source PDFs are copied into app-private storage and are never
 * included in DailyBeat's cloud backup snapshot.
 */
@Entity(
    tableName = "dsr_imports",
    indices = [
        Index(value = ["sha256"], unique = true),
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
