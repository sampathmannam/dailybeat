package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrCaseMention
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import com.dailybeat.app.data.model.DsrCaseSnapshot
import com.dailybeat.app.data.model.DsrCaseWork
import com.dailybeat.app.data.model.DsrProcedureCheck
import com.dailybeat.app.data.model.DsrCaseAudit
import kotlinx.coroutines.flow.Flow

// Legacy compatibility access only. No application screen or service calls this DAO;
// retaining the schema lets upgrades preserve the officer's old DSR records intact.
@Dao
interface DsrDao {
    @Query("SELECT * FROM dsr_imports WHERE sha256 = :sha256 ORDER BY active DESC, importedAt DESC, id DESC LIMIT 1")
    suspend fun importByHash(sha256: String): DsrImport?

    @Query("SELECT * FROM dsr_imports WHERE id = :id")
    suspend fun importById(id: String): DsrImport?

    @Query(
        "SELECT * FROM dsr_imports " +
            "WHERE reportType = :reportType AND reportDate IS :reportDate AND active = 1 " +
            "ORDER BY importedAt DESC LIMIT 1",
    )
    suspend fun activeImport(reportType: String, reportDate: String?): DsrImport?

    @Query(
        "SELECT * FROM dsr_imports WHERE reportType = :reportType AND active = 1 " +
            "ORDER BY reportDate DESC, importedAt DESC LIMIT 1",
    )
    fun observeLatestImport(reportType: String): Flow<DsrImport?>

    @Query("SELECT * FROM dsr_imports ORDER BY importedAt DESC LIMIT :limit")
    fun observeRecentImports(limit: Int): Flow<List<DsrImport>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImport(value: DsrImport)

    @Update
    suspend fun updateImport(value: DsrImport)

    @Query("UPDATE dsr_imports SET active = 0 WHERE id = :id")
    suspend fun deactivateImport(id: String)

    @Query("UPDATE dsr_imports SET active = 1 WHERE id = :id")
    suspend fun activateImport(id: String)

    @Query("SELECT * FROM dsr_cases WHERE caseKey = :caseKey LIMIT 1")
    suspend fun caseByKey(caseKey: String): DsrCase?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCases(values: List<DsrCase>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaseMentions(values: List<DsrCaseMention>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStationSnapshots(values: List<DsrStationSnapshot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetricSnapshots(values: List<DsrMetricSnapshot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertForecasts(values: List<DsrForecast>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQualityIssues(values: List<DsrQualityIssue>)

    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT c.* FROM dsr_case_snapshots c " +
            "WHERE c.importId = (SELECT id FROM dsr_imports " +
            "WHERE reportType = 'DSR' AND active = 1 ORDER BY reportDate DESC, importedAt DESC LIMIT 1) " +
            "ORDER BY CASE c.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 ELSE 3 END, c.stationName, c.crimeYear DESC, c.crimeNumber DESC " +
            "LIMIT :limit",
    )
    fun observeLatestCases(limit: Int): Flow<List<DsrCase>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCaseSnapshots(values: List<DsrCaseSnapshot>)

    @Query("SELECT * FROM dsr_case_snapshots WHERE importId = :importId ORDER BY caseKey")
    suspend fun snapshotsForImport(importId: String): List<DsrCaseSnapshot>

    @Query("SELECT * FROM dsr_metric_snapshots WHERE importId = :importId ORDER BY metricCode")
    suspend fun metricsForImport(importId: String): List<DsrMetricSnapshot>

    @Query("SELECT * FROM dsr_forecasts WHERE importId = :importId ORDER BY id")
    suspend fun forecastsForImport(importId: String): List<DsrForecast>

    @Query("SELECT * FROM dsr_quality_issues WHERE importId = :importId ORDER BY code, id")
    suspend fun issuesForImport(importId: String): List<DsrQualityIssue>

    @Query("SELECT * FROM dsr_station_snapshots WHERE importId = :importId ORDER BY stationCode")
    suspend fun stationsForImport(importId: String): List<DsrStationSnapshot>

    @Query("""
        SELECT s.* FROM dsr_cases c INNER JOIN dsr_case_snapshots s ON s.id = (
            SELECT candidate.id FROM dsr_case_snapshots candidate
            INNER JOIN dsr_imports i ON i.id = candidate.importId
            WHERE candidate.caseKey = c.caseKey
            ORDER BY i.active DESC, i.reportDate DESC, i.importedAt DESC, i.id DESC LIMIT 1
        ) ORDER BY s.caseKey
    """)
    fun observePreferredSources(): Flow<List<DsrCaseSnapshot>>

    @Query("""
        SELECT s.* FROM dsr_case_snapshots s INNER JOIN dsr_imports i ON i.id = s.importId
        WHERE s.caseKey = :caseKey
        ORDER BY i.active DESC, i.reportDate DESC, i.importedAt DESC, i.id DESC LIMIT 1
    """)
    suspend fun preferredSource(caseKey: String): DsrCaseSnapshot?

    @Query("SELECT * FROM dsr_case_work ORDER BY dueDate, caseKey")
    fun observeCaseWork(): Flow<List<DsrCaseWork>>

    @Query("SELECT * FROM dsr_case_work WHERE caseKey = :caseKey")
    suspend fun workByKey(caseKey: String): DsrCaseWork?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCaseWork(value: DsrCaseWork)

    @Query("SELECT * FROM dsr_procedure_checks ORDER BY caseKey, code")
    fun observeProcedureChecks(): Flow<List<DsrProcedureCheck>>

    @Query("SELECT * FROM dsr_procedure_checks WHERE caseKey = :caseKey")
    suspend fun checksForCase(caseKey: String): List<DsrProcedureCheck>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProcedureCheck(value: DsrProcedureCheck)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(value: DsrCaseAudit)

    @Query("SELECT * FROM dsr_case_audit WHERE caseKey = :caseKey ORDER BY createdAt DESC, rowid DESC")
    fun observeCaseAudit(caseKey: String): Flow<List<DsrCaseAudit>>

    @Query(
        "SELECT s.* FROM dsr_station_snapshots s " +
            "WHERE s.importId = (SELECT id FROM dsr_imports " +
            "WHERE reportType = 'DSR' AND active = 1 ORDER BY reportDate DESC, importedAt DESC LIMIT 1) " +
            "ORDER BY s.reportedCases DESC, s.stationName",
    )
    fun observeLatestStationSnapshots(): Flow<List<DsrStationSnapshot>>

    @Query(
        "SELECT f.* FROM dsr_forecasts f " +
            "WHERE f.importId = (SELECT id FROM dsr_imports " +
            "WHERE reportType = 'DSR' AND active = 1 ORDER BY reportDate DESC, importedAt DESC LIMIT 1) " +
            "ORDER BY CASE f.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 ELSE 3 END, f.stationName",
    )
    fun observeLatestForecasts(): Flow<List<DsrForecast>>

    @Query(
        "SELECT q.* FROM dsr_quality_issues q " +
            "INNER JOIN dsr_imports i ON i.id = q.importId " +
            "WHERE i.active = 1 AND i.id IN (" +
            "SELECT newest.id FROM dsr_imports newest WHERE newest.active = 1 AND newest.id = (" +
            "SELECT candidate.id FROM dsr_imports candidate " +
            "WHERE candidate.active = 1 AND candidate.reportType = newest.reportType " +
            "ORDER BY candidate.reportDate DESC, candidate.importedAt DESC, candidate.id DESC LIMIT 1)) " +
            "ORDER BY i.importedAt DESC, CASE q.severity WHEN 'ERROR' THEN 0 WHEN 'WARNING' THEN 1 ELSE 2 END",
    )
    fun observeLatestQualityIssues(): Flow<List<DsrQualityIssue>>

    @Query(
        "SELECT m.* FROM dsr_metric_snapshots m " +
            "INNER JOIN dsr_imports i ON i.id = m.importId " +
            "WHERE i.active = 1 " +
            "ORDER BY m.reportDate DESC, i.importedAt DESC",
    )
    fun observeActiveMetrics(): Flow<List<DsrMetricSnapshot>>
}
