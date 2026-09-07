package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.dailybeat.app.data.model.DsrCase
import com.dailybeat.app.data.model.DsrCaseMention
import com.dailybeat.app.data.model.DsrForecast
import com.dailybeat.app.data.model.DsrImport
import com.dailybeat.app.data.model.DsrMetricSnapshot
import com.dailybeat.app.data.model.DsrQualityIssue
import com.dailybeat.app.data.model.DsrStationSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface DsrDao {
    @Query("SELECT * FROM dsr_imports WHERE sha256 = :sha256 LIMIT 1")
    suspend fun importByHash(sha256: String): DsrImport?

    @Query(
        "SELECT * FROM dsr_imports " +
            "WHERE reportType = :reportType AND reportDate = :reportDate AND active = 1 " +
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

    @Query(
        "SELECT c.* FROM dsr_cases c " +
            "INNER JOIN dsr_case_mentions m ON m.caseKey = c.caseKey " +
            "WHERE m.importId = (SELECT id FROM dsr_imports " +
            "WHERE reportType = 'DSR' AND active = 1 ORDER BY reportDate DESC, importedAt DESC LIMIT 1) " +
            "ORDER BY CASE c.priority WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 " +
            "WHEN 'MEDIUM' THEN 2 ELSE 3 END, c.stationName, c.crimeYear DESC, c.crimeNumber DESC " +
            "LIMIT :limit",
    )
    fun observeLatestCases(limit: Int): Flow<List<DsrCase>>

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
            "SELECT newest.id FROM dsr_imports newest WHERE newest.active = 1 AND newest.importedAt = (" +
            "SELECT MAX(candidate.importedAt) FROM dsr_imports candidate " +
            "WHERE candidate.active = 1 AND candidate.reportType = newest.reportType)) " +
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
