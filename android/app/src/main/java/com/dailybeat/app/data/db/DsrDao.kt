package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Query
import com.dailybeat.app.data.model.DsrCaseSnapshot
import com.dailybeat.app.data.model.DsrImport

// Legacy compatibility access only. No application screen or service calls this DAO;
// retaining the schema lets upgrades preserve the officer's old DSR records intact.
//
// DSR itself is a separate app (see docs/DSR_COMMAND.md). The DSR entities stay declared in
// DailyBeatDb because Room derives its identity hash from the entity set — removing them would
// fail integrity validation on every existing install. Only the read paths that prove retention
// still works are kept here; the write and dashboard queries went with the feature.
@Dao
interface DsrDao {
    @Query("SELECT * FROM dsr_imports WHERE sha256 = :sha256 ORDER BY active DESC, importedAt DESC, id DESC LIMIT 1")
    suspend fun importByHash(sha256: String): DsrImport?

    @Query("SELECT * FROM dsr_imports WHERE id = :id")
    suspend fun importById(id: String): DsrImport?

    @Query("SELECT * FROM dsr_case_snapshots WHERE importId = :importId ORDER BY caseKey")
    suspend fun snapshotsForImport(importId: String): List<DsrCaseSnapshot>
}
