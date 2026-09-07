package com.dailybeat.app.data.repo

import androidx.room.withTransaction
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.db.DsrDao
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrCaseMention
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import kotlinx.coroutines.flow.Flow

data class DsrCommitBundle(
    val import: DsrImport,
    val cases: List<DsrCase>,
    val mentions: List<DsrCaseMention>,
    val stations: List<DsrStationSnapshot>,
    val metrics: List<DsrMetricSnapshot>,
    val forecasts: List<DsrForecast>,
    val issues: List<DsrQualityIssue>,
)

data class DsrCommitResult(
    val import: DsrImport,
    val alreadyExisted: Boolean,
    val replacedPreviousVersion: Boolean,
)

class DsrRepository(
    private val db: DailyBeatDb,
    private val dao: DsrDao,
) {
    fun observeLatestDsr(): Flow<DsrImport?> = dao.observeLatestImport("DSR")
    fun observeRecentImports(limit: Int = 20): Flow<List<DsrImport>> = dao.observeRecentImports(limit)
    fun observeLatestCases(limit: Int = 50): Flow<List<DsrCase>> = dao.observeLatestCases(limit)
    fun observeLatestStations(): Flow<List<DsrStationSnapshot>> = dao.observeLatestStationSnapshots()
    fun observeLatestForecasts(): Flow<List<DsrForecast>> = dao.observeLatestForecasts()
    fun observeLatestIssues(): Flow<List<DsrQualityIssue>> = dao.observeLatestQualityIssues()
    fun observeActiveMetrics(): Flow<List<DsrMetricSnapshot>> = dao.observeActiveMetrics()

    suspend fun importByHash(sha256: String): DsrImport? = dao.importByHash(sha256)

    suspend fun commit(bundle: DsrCommitBundle): DsrCommitResult = db.withTransaction {
        dao.importByHash(bundle.import.sha256)?.let {
            return@withTransaction DsrCommitResult(it, alreadyExisted = true, replacedPreviousVersion = false)
        }
        val previous = dao.activeImport(bundle.import.reportType, bundle.import.reportDate)
        if (previous != null) dao.deactivateImport(previous.id)
        val imported = bundle.import.copy(replacedImportId = previous?.id)
        dao.insertImport(imported)

        val mergedCases = bundle.cases.map { incoming ->
            val existing = dao.caseByKey(incoming.caseKey)
            if (existing == null) {
                incoming
            } else {
                incoming.copy(
                    firstSeenDate = minOf(existing.firstSeenDate, incoming.firstSeenDate),
                    lastSeenDate = maxOf(existing.lastSeenDate, incoming.lastSeenDate),
                    needsReview = existing.needsReview || incoming.needsReview,
                )
            }
        }
        if (mergedCases.isNotEmpty()) dao.upsertCases(mergedCases)
        if (bundle.mentions.isNotEmpty()) dao.upsertCaseMentions(bundle.mentions)
        if (bundle.stations.isNotEmpty()) dao.upsertStationSnapshots(bundle.stations)
        if (bundle.metrics.isNotEmpty()) dao.upsertMetricSnapshots(bundle.metrics)
        if (bundle.forecasts.isNotEmpty()) dao.upsertForecasts(bundle.forecasts)
        if (bundle.issues.isNotEmpty()) dao.upsertQualityIssues(bundle.issues)
        DsrCommitResult(imported, alreadyExisted = false, replacedPreviousVersion = previous != null)
    }
}
