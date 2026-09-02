package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONArray
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class PatrolActionOutboxTest {
    private lateinit var context: Context
    private lateinit var remote: RecordingRemote
    private var now = 1_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestAndroidKeyStore.install()
        rawPreferences().edit().clear().commit()
        remote = RecordingRemote()
        now = 1_000L
    }

    @After
    fun tearDown() {
        rawPreferences().edit().clear().commit()
        TestAndroidKeyStore.uninstall()
    }

    @Test
    fun `encrypted queue survives recreation and syncs actions in insertion order`() = runBlocking {
        val outbox = outbox()
        val visitId = outbox.enqueueVisit("mission-sensitive", "session-sensitive", "priority-north-gate")
        now += 1
        val observationId = outbox.enqueueUpdate(
            "mission-sensitive",
            "session-sensitive",
            "observation",
            "Sensitive checkpoint observation",
        )
        now += 1
        val contextId = outbox.enqueueUpdate(
            missionId = "mission-sensitive",
            sessionId = null,
            category = "review_context",
            detail = "Road closure required a safe diversion",
            reviewId = "review-request-7",
        )

        assertEquals(3, outbox.pendingCount())
        val raw = rawPreferences().all.toString()
        assertFalse(raw.contains("mission-sensitive"))
        assertFalse(raw.contains("priority-north-gate"))
        assertFalse(raw.contains("Sensitive checkpoint observation"))
        assertFalse(raw.contains("review-request-7"))
        assertFalse(raw.contains("Road closure required a safe diversion"))

        val recreated = outbox()
        assertEquals(3, recreated.pendingCount())
        assertEquals(3, recreated.syncPending().getOrThrow())
        assertEquals(
            listOf(
                "visit:session-sensitive:priority-north-gate:$visitId:1000",
                "update:session-sensitive:observation:Sensitive checkpoint observation:$observationId:1001:",
                "update::review_context:Road closure required a safe diversion:$contextId:1002:review-request-7",
            ),
            remote.attempts,
        )
        assertEquals(0, recreated.pendingCount())
    }

    @Test
    fun `partial failure removes acknowledged prefix and retains remaining actions`() = runBlocking {
        val outbox = outbox()
        outbox.enqueueVisit("mission-1", "session-1", "priority-1")
        now += 1
        val failedUpdateId = outbox.enqueueUpdate(
            "mission-1",
            "session-1",
            "operational_deviation",
            "Road blocked",
        )
        now += 1
        val remainingVisitId = outbox.enqueueVisit("mission-1", "session-1", "priority-2")
        remote.failOnAttempt = 2

        val firstSync = outbox.syncPending()

        assertTrue(firstSync.isFailure)
        assertEquals(2, outbox.pendingCount())
        assertTrue(remote.attempts[0].startsWith("visit:session-1:priority-1:"))
        assertEquals(
            "update:session-1:operational_deviation:Road blocked:$failedUpdateId:1001:",
            remote.attempts[1],
        )

        remote.attempts.clear()
        remote.failOnAttempt = null
        assertEquals(2, outbox.syncPending().getOrThrow())
        assertEquals(
            listOf(
                "update:session-1:operational_deviation:Road blocked:$failedUpdateId:1001:",
                "visit:session-1:priority-2:$remainingVisitId:1002",
            ),
            remote.attempts,
        )
        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `clear removes every pending action across recreation`() {
        val outbox = outbox()
        outbox.enqueueVisit("mission-1", "session-1", "priority-1")
        outbox.enqueueUpdate("mission-1", "session-1", "safety_event", "Crowd control requested")
        assertEquals(2, outbox.pendingCount())

        outbox.clear()

        assertEquals(0, outbox.pendingCount())
        assertEquals(0, outbox().pendingCount())
    }

    @Test
    fun `damaged encrypted queue fails closed`() {
        val outbox = outbox()
        outbox.enqueueVisit("mission-1", "session-1", "priority-1")
        corruptEncryptedPayload()

        val error = assertThrows(IllegalStateException::class.java) {
            outbox.pendingCount()
        }

        assertEquals("The secure patrol action queue could not be read.", error.message)
    }

    @Test
    fun `retention removes due missions and ignores action event timestamps`() = runBlocking {
        now = 9_999L
        outbox().enqueueVisit("mission-old", "session-old", "priority-old")
        now = 10_000L
        outbox().enqueueVisit("mission-boundary", "session-boundary", "priority-boundary")
        now = 10_001L
        val retainedId = outbox().enqueueVisit("mission-young", "session-young", "priority-young")

        val result = outbox().purgeForMissionDeadlines(
            setOf("mission-old", "mission-boundary"),
        )

        assertEquals(2, result.expiredCount)
        assertEquals(0, result.malformedCount)
        assertEquals(setOf("mission-old", "mission-boundary"), result.affectedMissionIds)
        assertEquals(1, outbox().pendingCount())
        assertEquals(1, outbox().syncPending().getOrThrow())
        assertEquals(
            listOf("visit:session-young:priority-young:$retainedId:10001"),
            remote.attempts,
        )
    }

    @Test
    fun `retention upgrades readable legacy action and dead letters malformed row`() = runBlocking {
        val rows = JSONArray()
            .put(
                JSONObject()
                    .put("id", "legacy-young")
                    .put("type", "visit")
                    .put("missionId", "mission-young")
                    .put("sessionId", "session-young")
                    .put("priorityLocationId", "priority-young")
                    .put("category", "")
                    .put("detail", "")
                    .put("reviewId", "")
                    .put("createdAtMs", 20_001L),
            )
            .put(
                JSONObject()
                    .put("id", "legacy-corrupt")
                    .put("type", "visit")
                    .put("missionId", "mission-corrupt")
                    .put("sessionId", "session-corrupt")
                    .put("priorityLocationId", "priority-corrupt"),
            )
        check(securePreferences().edit().putString(KEY_ACTIONS, rows.toString()).commit())

        val plan = outbox().inspectRetention(emptySet())
        assertEquals(1, plan.malformedCount)
        val result = outbox().purgeForMissionDeadlines(emptySet())

        assertEquals(0, result.expiredCount)
        assertEquals(1, result.malformedCount)
        assertEquals(setOf("mission-corrupt"), result.affectedMissionIds)
        assertEquals(1, outbox().pendingCount())
        assertEquals(1, outbox().syncPending().getOrThrow())
        assertEquals(
            listOf("visit:session-young:priority-young:legacy-young:20001"),
            remote.attempts,
        )
    }

    @Test
    fun `retention dead letters unreadable encrypted container with an incident count`() {
        val outbox = outbox()
        outbox.enqueueVisit("mission-1", "session-1", "priority-1")
        corruptEncryptedPayload()

        val result = outbox.purgeForMissionDeadlines(emptySet())

        assertEquals(1, result.unreadableContainerCount)
        assertEquals(1, result.discardedCount)
        assertEquals(0, outbox.pendingCount())
    }

    private fun outbox() = PatrolActionOutbox(context, remote) { now }

    private fun rawPreferences() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun securePreferences() = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun corruptEncryptedPayload() {
        val encryptedDataKey = rawPreferences().all.keys.single { key ->
            !key.startsWith("__androidx_security_crypto_encrypted_prefs_")
        }
        check(rawPreferences().edit().putString(encryptedDataKey, "not-valid-ciphertext").commit())
    }

    private class RecordingRemote : PatrolGridRemote {
        val attempts = mutableListOf<String>()
        var failOnAttempt: Int? = null

        override val isConfigured = true
        override fun currentSession(): BackupSession? = null
        override suspend fun signIn(email: String, password: String) = unsupported<PatrolGridIdentity>()
        override suspend fun loadIdentity() = unsupported<PatrolGridIdentity>()
        override suspend fun loadSnapshot(activeMissionId: String?) = unsupported<PatrolGridRemoteSnapshot>()
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
        override suspend fun endSession(sessionId: String, reason: String, endedAtMs: Long) =
            unsupported<Unit>()
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
        ): Result<Unit> {
            attempts += "visit:$sessionId:$priorityLocationId:$clientVisitId:$visitedAtMs"
            return resultForCurrentAttempt()
        }

        override suspend fun addFieldUpdate(
            sessionId: String?,
            category: String,
            detail: String,
            clientUpdateId: String,
            occurredAtMs: Long,
            reviewId: String?,
        ): Result<Unit> {
            attempts += "update:${sessionId.orEmpty()}:$category:$detail:$clientUpdateId:$occurredAtMs:${reviewId.orEmpty()}"
            return resultForCurrentAttempt()
        }

        override fun signOut() = Unit

        private fun resultForCurrentAttempt(): Result<Unit> = if (attempts.size == failOnAttempt) {
            Result.failure(IllegalStateException("synthetic sync failure"))
        } else {
            Result.success(Unit)
        }

        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }

    private companion object {
        const val FILE_NAME = "patrolgrid_action_outbox"
        const val KEY_ACTIONS = "pending_actions"
        const val RETENTION_MS = 365L * 24L * 60L * 60L * 1_000L
    }
}
