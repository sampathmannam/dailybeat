package com.dailybeat.app.dsr

import android.net.Uri
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrCaseMention
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import com.dailybeat.app.data.repo.DsrCommitBundle
import com.dailybeat.app.data.repo.DsrRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DsrImportService(
    private val documentStore: DsrDocumentStore,
    private val textExtractor: DsrPdfTextExtractor,
    private val parser: DsrReportParser,
    private val repository: DsrRepository,
) {
    suspend fun import(uri: Uri): DsrImportOutcome = withContext(Dispatchers.IO) {
        val stored = documentStore.copyFrom(uri)
        repository.importByHash(stored.sha256)?.let { existing ->
            return@withContext DsrImportOutcome.AlreadyImported(existing.id, existing.originalFileName)
        }
        try {
            val pages = textExtractor.extract(stored.file)
            val parsed = parser.parse(stored.originalFileName, pages)
            val importId = UUID.randomUUID().toString()
            val reportDate = parsed.reportDate
            val importedAt = System.currentTimeMillis()
            val import = DsrImport(
                id = importId,
                sha256 = stored.sha256,
                originalFileName = stored.originalFileName,
                storedFileName = stored.file.name,
                fileSizeBytes = stored.sizeBytes,
                reportType = parsed.reportType.name,
                reportDate = reportDate,
                importedAt = importedAt,
                pageCount = parsed.pageCount,
                caseCount = parsed.cases.size,
                forecastCount = parsed.forecasts.size,
                issueCount = parsed.issues.size,
                qualityScore = parsed.qualityScore,
            )
            val cases = parsed.cases.map { value ->
                DsrCase(
                    caseKey = value.caseKey,
                    stationCode = value.stationCode,
                    stationName = value.stationName,
                    crimeNumber = value.crimeNumber,
                    crimeYear = value.crimeYear,
                    displayCrimeNumber = value.displayCrimeNumber,
                    head = value.head,
                    lawSections = value.lawSections,
                    priority = value.priority.name,
                    firstSeenDate = requireNotNull(reportDate),
                    lastSeenDate = reportDate,
                    needsReview = value.needsReview,
                )
            }
            val mentions = parsed.cases.map { value ->
                DsrCaseMention(
                    mentionKey = "$importId|${value.caseKey}",
                    importId = importId,
                    caseKey = value.caseKey,
                    reportDate = requireNotNull(reportDate),
                    sourcePage = value.sourcePage,
                )
            }
            val stations = parsed.stationSnapshots.map { value ->
                DsrStationSnapshot(
                    id = "$importId|${value.stationCode}",
                    importId = importId,
                    reportDate = requireNotNull(reportDate),
                    stationCode = value.stationCode,
                    stationName = value.stationName,
                    reportedCases = value.reportedCases,
                    chargedCases = value.chargedCases,
                    otherDisposals = value.otherDisposals,
                    eSummonsReceived = value.eSummonsReceived,
                    eSummonsServed = value.eSummonsServed,
                    eSakshyaRecorded = value.eSakshyaRecorded,
                    eSakshyaLinked = value.eSakshyaLinked,
                    mvDdCases = value.mvDdCases,
                    mvOtherCases = value.mvOtherCases,
                    takenOnFile = value.takenOnFile,
                    convictions = value.convictions,
                    acquittals = value.acquittals,
                )
            }
            val metrics = parsed.metrics.map { value ->
                DsrMetricSnapshot(
                    id = "$importId|${value.metricCode}",
                    importId = importId,
                    reportDate = reportDate,
                    reportType = parsed.reportType.name,
                    metricCode = value.metricCode,
                    metricValue = value.value,
                    semantics = value.semantics.name,
                )
            }
            val forecasts = parsed.forecasts.map { value ->
                DsrForecast(
                    id = "$importId|${value.stableKey}",
                    importId = importId,
                    reportDate = requireNotNull(reportDate),
                    eventDate = value.eventDate,
                    stationCode = value.stationCode,
                    stationName = value.stationName,
                    category = value.category,
                    priority = value.priority.name,
                    expectedCrowd = value.expectedCrowd,
                    details = value.details,
                )
            }
            val issues = parsed.issues.mapIndexed { index, value ->
                DsrQualityIssue(
                    id = "$importId|${value.code}|$index",
                    importId = importId,
                    severity = value.severity.name,
                    code = value.code,
                    message = value.message,
                    caseKey = value.caseKey,
                    sourcePage = value.sourcePage,
                )
            }
            val result = repository.commit(
                DsrCommitBundle(import, cases, mentions, stations, metrics, forecasts, issues),
            )
            if (result.alreadyExisted) {
                DsrImportOutcome.AlreadyImported(result.import.id, result.import.originalFileName)
            } else {
                DsrImportOutcome.Imported(
                    importId = result.import.id,
                    reportType = parsed.reportType,
                    reportDate = reportDate,
                    caseCount = cases.size,
                    issueCount = issues.size,
                    qualityScore = parsed.qualityScore,
                    replacedPreviousVersion = result.replacedPreviousVersion,
                )
            }
        } catch (error: Throwable) {
            documentStore.removeIfUnreferenced(stored)
            throw error
        }
    }
}
