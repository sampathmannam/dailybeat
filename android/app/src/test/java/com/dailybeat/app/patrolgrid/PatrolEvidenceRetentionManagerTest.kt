package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PatrolEvidenceRetentionManagerTest {
    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var settings: SettingsRepository
    private lateinit var outbox: PatrolActionOutbox
    private lateinit var retentionStore: PatrolMissionRetentionStore
    private lateinit var snapshotCache: PatrolGridSnapshotCache
    private var nowMs = 50_000L
    private var actionTimeMs = 1L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestAndroidKeyStore.install()
        listOf(SETTINGS_FILE, OUTBOX_FILE, RETENTION_FILE, SNAPSHOT_FILE).forEach(::clearPrefs)
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        outbox = PatrolActionOutbox(context, NoopRemote) { actionTimeMs }
        retentionStore = PatrolMissionRetentionStore(context)
        snapshotCache = PatrolGridSnapshotCache(context, retentionStore) { nowMs }
    }

    @After
    fun tearDown() {
        db.close()
        listOf(SETTINGS_FILE, OUTBOX_FILE, RETENTION_FILE, SNAPSHOT_FILE).forEach(::clearPrefs)
        TestAndroidKeyStore.uninstall()
    }

    @Test
    fun `all mission evidence expires exactly at server deadline regardless of event times`() = runBlocking {
        recordMission("due", nowMs)
        recordMission("future", nowMs + 1L)
        insertPoint(1, "due", timestampMs = nowMs - YEAR_MS * 2)
        insertPoint(2, "due", timestampMs = nowMs + YEAR_MS)
        insertPoint(3, "future", timestampMs = 1L)
        actionTimeMs = nowMs + YEAR_MS
        outbox.enqueueVisit("due", "session-due", "priority-due")
        actionTimeMs = 1L
        outbox.enqueueVisit("future", "session-future", "priority-future")

        val report = manager().enforce().getOrThrow()

        assertEquals(2, report.discardedTrackPointCount)
        assertEquals(1, report.discardedActionCount)
        assertTrue(db.patrolTracks().forMission("due").isEmpty())
        assertEquals(listOf(3L), db.patrolTracks().forMission("future").map { it.id })
        assertEquals(1, outbox.pendingCount())
        assertEquals(3L, settings.get().patrolRetentionDiscardedItemCount)
        assertTrue(settings.get().patrolRetentionIncidentUnresolved)
    }

    @Test
    fun `one millisecond before authoritative deadline preserves old evidence`() = runBlocking {
        recordMission("mission-1", nowMs + 1L)
        insertPoint(1, "mission-1", timestampMs = 1L)
        actionTimeMs = 1L
        outbox.enqueueUpdate("mission-1", "session-1", "observation", "Old but still retained")

        val report = manager().enforce().getOrThrow()

        assertEquals(0, report.discardedItemCount)
        assertEquals(1, outbox.pendingCount())
        assertEquals(1, db.patrolTracks().countForMission("mission-1"))
    }

    @Test
    fun `due mission clears pending close and owner after atomic evidence deletion`() = runBlocking {
        recordMission("mission-1", nowMs)
        insertPoint(1, "mission-1", 1L)
        settings.setPendingPatrolClose("session-1", "mission-1", endedAtMs = nowMs - 1L)
        settings.setPatrolEvidenceOwner("user-1")

        val report = manager().enforce().getOrThrow()

        assertTrue(report.pendingCloseCleared)
        assertTrue(report.evidenceOwnerCleared)
        assertNull(settings.get().pendingPatrolCloseSessionId)
        assertNull(settings.get().patrolEvidenceOwnerId)
    }

    @Test
    fun `malformed pending close is journaled and cleared without using its timestamp as a clock`() =
        runBlocking {
            context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE).edit()
                .putString("pending_patrol_close_session", "session-only")
                .commit()

            val report = manager().enforce().getOrThrow()

            assertTrue(report.pendingCloseCleared)
            assertEquals(1, report.discardedItemCount)
            assertNull(settings.get().pendingPatrolCloseSessionId)
            assertEquals(0, settings.get().patrolRetentionDeletionIntentCount)
        }

    @Test
    fun `unknown clock after bounded close grace fails closed and blocks capture`() = runBlocking {
        insertPoint(1, "unknown", nowMs)
        settings.setPendingPatrolClose("session-1", "unknown", endedAtMs = nowMs - DAY_MS - 1L)

        val result = manager().enforce()

        assertTrue(result.isFailure)
        assertNotNull(settings.get().patrolRetentionEnforcementFailureAtMs)
        assertEquals(1, db.patrolTracks().countForMission("unknown"))
    }

    @Test
    fun `known open mission is temporarily retained while close clock is pending`() = runBlocking {
        retentionStore.recordAuthoritativeMissions(listOf(mission("active", null)))
        insertPoint(1, "active", 1L)
        settings.setActivePatrolMission("active")
        settings.setActivePatrolDeadline(nowMs + 1L)

        val result = manager().enforce()

        assertTrue(result.isSuccess)
        assertEquals(1, db.patrolTracks().countForMission("active"))
        assertNull(settings.get().patrolRetentionEnforcementFailureAtMs)
    }

    @Test
    fun `year offline reboot blocks then authoritative reconnect purges at server deadline`() = runBlocking {
        nowMs = YEAR_MS + 50_000L
        insertPoint(1, "offline", timestampMs = 10L)
        settings.setPendingPatrolClose("session-offline", "offline", endedAtMs = 49_999L)

        assertTrue(manager().enforce().isFailure)
        val recreatedSettings = SettingsRepository(context)
        val recreatedStore = PatrolMissionRetentionStore(context)
        val recreatedManager = PatrolEvidenceRetentionManager(
            trackDao = db.patrolTracks(),
            actionOutbox = PatrolActionOutbox(context, NoopRemote) { actionTimeMs },
            retentionStore = recreatedStore,
            snapshotCache = PatrolGridSnapshotCache(context, recreatedStore) { nowMs },
            settings = recreatedSettings,
            retentionDays = PATROLGRID_LOCAL_RETENTION_DAYS,
            clock = { nowMs },
        )
        assertTrue(recreatedManager.enforce().isFailure)

        recreatedStore.recordAuthoritativeMissions(listOf(mission("offline", nowMs)))
        val recovered = recreatedManager.enforce().getOrThrow()

        assertEquals(1, recovered.discardedTrackPointCount)
        assertNull(recreatedSettings.get().patrolRetentionEnforcementFailureAtMs)
        assertNull(recreatedSettings.get().pendingPatrolCloseSessionId)
    }

    @Test
    fun `process recreation completes aggregate journal after evidence was already removed`() = runBlocking {
        assertTrue(settings.beginPatrolRetentionDeletion(4, nowMs - 1L))

        val report = manager().enforce().getOrThrow()

        assertEquals(0, report.discardedItemCount)
        assertEquals(4L, settings.get().patrolRetentionDiscardedItemCount)
        assertEquals(0, settings.get().patrolRetentionDeletionIntentCount)
        assertTrue(settings.get().patrolRetentionIncidentUnresolved)
    }

    @Test
    fun `authoritative server removal dead letters mission once and ends retry loop`() = runBlocking {
        recordMission("mission-1", null)
        insertPoint(1, "mission-1", 1L)
        outbox.enqueueVisit("mission-1", "session-1", "priority-1")
        settings.setPendingPatrolClose("session-1", "mission-1", endedAtMs = nowMs)

        val report = manager().discardUnavailableMission("mission-1").getOrThrow()

        assertEquals(1, report.discardedActionCount)
        assertEquals(1, report.discardedTrackPointCount)
        assertTrue(report.pendingCloseCleared)
        assertTrue(db.patrolTracks().forMission("mission-1").isEmpty())
        assertEquals(0, outbox.pendingCount())
        assertEquals(3L, settings.get().patrolRetentionDiscardedItemCount)
    }

    @Test
    fun `authoritative removal of one mission cannot release another unknown mission`() = runBlocking {
        insertPoint(1, "purged", 1L)
        insertPoint(2, "still-unknown", 1L)

        val cleanup = manager().discardUnavailableMission("purged").getOrThrow()
        val strictFollowUp = manager().enforce()

        assertEquals(1, cleanup.discardedTrackPointCount)
        assertTrue(db.patrolTracks().forMission("purged").isEmpty())
        assertEquals(1, db.patrolTracks().countForMission("still-unknown"))
        assertTrue(strictFollowUp.isFailure)
        assertTrue(strictFollowUp.exceptionOrNull() is PatrolMissionClockUnavailableException)
        assertNotNull(settings.get().patrolRetentionEnforcementFailureAtMs)
    }

    @Test
    fun `acknowledgement clears warning but preserves aggregate audit record`() = runBlocking {
        assertTrue(settings.recordPatrolRetentionIncident(2, nowMs))
        assertTrue(settings.acknowledgePatrolRetentionIncident())

        val recreated = SettingsRepository(context).get()
        assertFalse(recreated.patrolRetentionIncidentUnresolved)
        assertEquals(2L, recreated.patrolRetentionDiscardedItemCount)
        assertEquals(nowMs, recreated.patrolRetentionIncidentAtMs)
    }

    private fun manager() = PatrolEvidenceRetentionManager(
        trackDao = db.patrolTracks(),
        actionOutbox = outbox,
        retentionStore = retentionStore,
        snapshotCache = snapshotCache,
        settings = settings,
        retentionDays = PATROLGRID_LOCAL_RETENTION_DAYS,
        clock = { nowMs },
    )

    private fun recordMission(id: String, deadline: Long?) {
        retentionStore.recordAuthoritativeMissions(listOf(mission(id, deadline)))
    }

    private fun mission(id: String, deadline: Long?) = PatrolMission(
        id = id,
        title = id,
        dutyWindow = "window",
        unitName = "unit",
        personnelCount = 1,
        status = if (deadline == null) PatrolMissionStatus.ACTIVE else PatrolMissionStatus.COMPLETED,
        statusLabel = "status",
        context = "context",
        priorityLocations = emptyList(),
        lastUpdateLabel = "now",
        retentionUntilEpochMs = deadline,
    )

    private suspend fun insertPoint(id: Long, missionId: String, timestampMs: Long) {
        db.patrolTracks().insert(
            PatrolTrackPoint(
                id = id,
                missionId = missionId,
                timestampMs = timestampMs,
                encryptedPayload = byteArrayOf(1, 2, 3),
                sessionId = "session-$missionId",
                clientPointId = "point-$id",
            ),
        )
    }

    private fun clearPrefs(file: String) {
        context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private object NoopRemote : PatrolGridRemote {
        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signIn(email: String, password: String) = unsupported<PatrolGridIdentity>()
        override suspend fun loadIdentity() = unsupported<PatrolGridIdentity>()
        override suspend fun loadSnapshot(activeMissionId: String?) = unsupported<PatrolGridRemoteSnapshot>()
        override suspend fun loadEvidenceTrail(sessionId: String) = unsupported<PatrolEvidenceTrail>()
        override suspend fun loadAssignmentOptions() = unsupported<PatrolAssignmentOptions>()
        override suspend fun createAssignment(draft: PatrolAssignmentDraft) = unsupported<Unit>()
        override suspend fun submitReview(
            missionId: String,
            expectedVersion: Int,
            outcome: SupervisorReviewOutcome,
            notes: String,
        ) = unsupported<Int>()
        override suspend fun startSession(missionId: String, installationId: String, appVersion: String) =
            unsupported<String>()
        override suspend fun endSession(sessionId: String, reason: String, endedAtMs: Long) = unsupported<Unit>()
        override suspend fun uploadTrackPoints(
            missionId: String,
            sessionId: String,
            points: List<RemoteTrackPoint>,
        ) = unsupported<Unit>()
        override suspend fun markPriorityVisited(
            sessionId: String,
            priorityLocationId: String,
            clientVisitId: String,
            visitedAtMs: Long,
        ) = unsupported<Unit>()
        override suspend fun addFieldUpdate(
            sessionId: String?,
            category: String,
            detail: String,
            clientUpdateId: String,
            occurredAtMs: Long,
            reviewId: String?,
        ) = unsupported<Unit>()
        override fun signOut() = Unit
        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    private companion object {
        const val SETTINGS_FILE = "dailybeat_settings"
        const val OUTBOX_FILE = "patrolgrid_action_outbox"
        const val RETENTION_FILE = "patrolgrid_mission_retention"
        const val SNAPSHOT_FILE = "patrolgrid_mission_cache"
        const val DAY_MS = 24L * 60L * 60L * 1_000L
        const val YEAR_MS = 365L * DAY_MS
    }
}
