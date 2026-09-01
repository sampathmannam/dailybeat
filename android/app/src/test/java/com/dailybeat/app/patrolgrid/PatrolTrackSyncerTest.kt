package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.db.PatrolTrackDao
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.security.PatrolCoordinates
import com.dailybeat.app.security.PatrolTrackCipher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PatrolTrackSyncerTest {
    private lateinit var context: Context
    private lateinit var settings: SettingsRepository
    private lateinit var dao: FakeDao
    private lateinit var remote: FakeRemote

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        settings = SettingsRepository(context)
        dao = FakeDao()
        remote = FakeRemote()
    }

    @Test
    fun `pending encrypted points upload before session closes`() = runBlocking {
        dao.rows += point(1)
        dao.rows += point(2)
        settings.setPendingPatrolClose("session-1", "mission-1")
        val syncer = syncer()

        val uploaded = syncer.syncAndClosePendingSession().getOrThrow()

        assertEquals(2, uploaded)
        assertEquals(listOf(1, 2), remote.uploaded.single().map { it.sequenceNumber })
        assertEquals(listOf("session-1"), remote.closedSessions)
        assertTrue(dao.rows.all { it.syncedAtMs == 9_000L })
        assertEquals(null, settings.get().pendingPatrolCloseSessionId)
    }

    @Test
    fun `failed upload keeps evidence pending and does not close session`() = runBlocking {
        dao.rows += point(1)
        settings.setPendingPatrolClose("session-1", "mission-1")
        remote.uploadFailure = IllegalStateException("offline")

        val result = syncer().syncAndClosePendingSession()

        assertTrue(result.isFailure)
        assertEquals(null, dao.rows.single().syncedAtMs)
        assertTrue(remote.closedSessions.isEmpty())
        assertEquals("session-1", settings.get().pendingPatrolCloseSessionId)
    }

    private fun syncer() = PatrolTrackSyncer(
        dao = dao,
        cipher = PatrolTrackCipher(context),
        remote = remote,
        settings = settings,
        clock = { 9_000L },
        coordinateDecoder = { PatrolCoordinates(13.0, 77.5, 8f) },
    )

    private fun point(id: Long) = PatrolTrackPoint(
        id = id,
        missionId = "mission-1",
        timestampMs = 1_000L + id,
        encryptedPayload = byteArrayOf(1, 2, 3),
        sessionId = "session-1",
        clientPointId = "point-$id",
    )

    private class FakeDao : PatrolTrackDao {
        val rows = mutableListOf<PatrolTrackPoint>()
        override suspend fun insert(point: PatrolTrackPoint): Long {
            rows += point
            return point.id
        }
        override suspend fun forMission(missionId: String) = rows.filter { it.missionId == missionId }
        override suspend fun countForMission(missionId: String) = rows.count { it.missionId == missionId }
        override suspend fun pending(missionId: String?, limit: Int) = rows
            .filter { it.syncedAtMs == null && it.sessionId != null && (missionId == null || it.missionId == missionId) }
            .take(limit)
        override suspend fun markSynced(ids: List<Long>, syncedAtMs: Long) {
            ids.forEach { id ->
                val index = rows.indexOfFirst { it.id == id }
                rows[index] = rows[index].copy(syncedAtMs = syncedAtMs)
            }
        }
        override suspend fun pendingCount() = rows.count { it.syncedAtMs == null && it.sessionId != null }
        override suspend fun deleteForMission(missionId: String) {
            rows.removeAll { it.missionId == missionId }
        }
    }

    private class FakeRemote : PatrolGridRemote {
        var uploadFailure: Exception? = null
        val uploaded = mutableListOf<List<RemoteTrackPoint>>()
        val closedSessions = mutableListOf<String>()
        override val isConfigured = true
        override fun currentSession() = BackupSession("user-1", "test@example.com", "a", "r", Long.MAX_VALUE)
        override suspend fun signIn(email: String, password: String) = unsupported<PatrolGridIdentity>()
        override suspend fun loadIdentity() = unsupported<PatrolGridIdentity>()
        override suspend fun loadSnapshot(activeMissionId: String?) = unsupported<PatrolGridRemoteSnapshot>()
        override suspend fun loadAssignmentOptions() = unsupported<PatrolAssignmentOptions>()
        override suspend fun createAssignment(draft: com.dailybeat.app.data.model.PatrolAssignmentDraft) = unsupported<Unit>()
        override suspend fun startSession(missionId: String, installationId: String, appVersion: String) =
            unsupported<String>()
        override suspend fun endSession(sessionId: String, reason: String): Result<Unit> {
            closedSessions += sessionId
            return Result.success(Unit)
        }
        override suspend fun uploadTrackPoints(
            missionId: String,
            sessionId: String,
            points: List<RemoteTrackPoint>,
        ): Result<Unit> {
            uploadFailure?.let { return Result.failure(it) }
            uploaded += points
            return Result.success(Unit)
        }
        override suspend fun markPriorityVisited(
            missionId: String,
            priorityLocationId: String,
            clientVisitId: String,
            visitedAtMs: Long,
        ) = unsupported<Unit>()
        override suspend fun addFieldUpdate(
            missionId: String,
            category: String,
            detail: String,
            clientUpdateId: String,
            occurredAtMs: Long,
        ) = unsupported<Unit>()
        override fun signOut() = Unit

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }
}
