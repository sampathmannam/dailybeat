package com.dailybeat.app.patrolgrid

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PatrolGridSnapshotCacheTest {
    private lateinit var context: Context
    private var now = 10_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestAndroidKeyStore.install()
        rawPreferences().edit().clear().commit()
        context.getSharedPreferences(RETENTION_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        now = 10_000L
    }

    @After
    fun tearDown() {
        rawPreferences().edit().clear().commit()
        context.getSharedPreferences(RETENTION_FILE, Context.MODE_PRIVATE).edit().clear().commit()
        TestAndroidKeyStore.uninstall()
    }

    @Test
    fun `encrypted snapshot round trips all mission evidence and remains user isolated`() {
        val expected = snapshot()
        cache().save(expected)

        val raw = rawPreferences().all.toString()
        assertFalse(raw.contains("user-sensitive"))
        assertFalse(raw.contains("Night perimeter"))
        assertFalse(raw.contains("Sensitive north gate instruction"))
        assertFalse(raw.contains("review-request-9"))
        assertFalse(raw.contains("Explain why the north gate was skipped"))
        assertFalse(raw.contains("session-sensitive-9"))
        assertFalse(raw.contains("Officer Evidence"))
        assertFalse(raw.contains("Manual visit because GPS was unavailable"))

        assertNull(cache().load("different-user"))
        assertEquals(expected, cache().load("user-sensitive"))
    }

    @Test
    fun `snapshot expires after the requested maximum age and rejects future timestamps`() {
        val cache = cache()
        cache.save(snapshot())

        now = 10_101L
        assertNull(cache.load("user-sensitive", maxAgeMs = 100L))
        assertEquals(0, encryptedPayloadCount())

        now = 10_000L
        cache.save(snapshot())
        now = 9_999L
        assertNull(cache.load("user-sensitive", maxAgeMs = 100L))
        assertEquals(0, encryptedPayloadCount())
    }

    @Test
    fun `damaged encrypted snapshot is treated as unavailable`() {
        val cache = cache()
        cache.save(snapshot())
        corruptEncryptedPayload()

        assertNull(cache.load("user-sensitive"))
        assertEquals(0, encryptedPayloadCount())
    }

    @Test
    fun `legacy cache with a mission-wide trail is rejected during upgrade`() {
        val cache = cache()
        cache.save(snapshot())
        val decrypted = decryptedPreferences()
        val legacy = JSONObject(requireNotNull(decrypted.getString(KEY_SNAPSHOT, null)))
            .apply { remove("schemaVersion") }
        check(decrypted.edit().putString(KEY_SNAPSHOT, legacy.toString()).commit())

        assertEquals(1, cache.retentionDiscardCount(now))
        assertNull(cache.load("user-sensitive"))
        assertEquals(0, encryptedPayloadCount())
    }

    @Test
    fun `clear removes cached briefing across recreation`() {
        val cache = cache()
        cache.save(snapshot())
        assertNotNull(cache.load("user-sensitive"))

        cache.clear()

        assertNull(cache.load("user-sensitive"))
        assertNull(cache().load("user-sensitive"))
    }

    @Test
    fun `retention sweep removes snapshot exactly at cache boundary`() {
        val cache = cache()
        cache.save(snapshot())
        now += DAY_MS - 1L
        assertEquals(0, cache.retentionDiscardCount(now))
        assertEquals(0, cache.purgeForRetentionIfNeeded(now))

        now += 1L
        assertEquals(1, cache.retentionDiscardCount(now))
        assertEquals(1, cache.purgeForRetentionIfNeeded(now))
        assertEquals(0, encryptedPayloadCount())
    }

    @Test
    fun `mission deadline removes a recently refreshed evidence snapshot exactly on time`() {
        val cache = cache()
        cache.save(
            snapshot().copy(
                missions = snapshot().missions.map {
                    it.copy(retentionUntilEpochMs = now + 100L)
                },
            ),
        )
        now += 99L
        assertEquals(0, cache.retentionDiscardCount(now))

        now += 1L
        assertEquals(1, cache.retentionDiscardCount(now))
        assertEquals(1, cache.purgeForRetentionIfNeeded(now))
        assertEquals(0, encryptedPayloadCount())
    }

    @Test
    fun `authoritative due mission set invalidates a cache even if its deadline is absent`() {
        val cache = cache()
        cache.save(
            snapshot().copy(
                missions = snapshot().missions.map { it.copy(retentionUntilEpochMs = null) },
            ),
        )

        assertEquals(
            1,
            cache.retentionDiscardCount(nowMs = now, dueMissionIds = setOf("mission-1")),
        )
    }

    @Test
    fun `stale nonterminal snapshot cannot erase learned server deadline`() {
        val store = PatrolMissionRetentionStore(context)
        val deadline = 99_000L
        store.recordAuthoritativeMissions(
            listOf(snapshot().missions.single().copy(retentionUntilEpochMs = deadline)),
        )
        store.recordAuthoritativeMissions(
            listOf(snapshot().missions.single().copy(status = PatrolMissionStatus.ACTIVE, retentionUntilEpochMs = null)),
        )

        assertEquals(emptySet<String>(), store.missionIdsWithoutDeadline())
        assertEquals(setOf("mission-1"), store.dueMissionIds(deadline))
    }

    private fun cache() = PatrolGridSnapshotCache(context) { now }

    private fun rawPreferences() = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun decryptedPreferences() = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun encryptedPayloadCount(): Int = rawPreferences().all.keys.count { key ->
        !key.startsWith("__androidx_security_crypto_encrypted_prefs_")
    }

    private fun corruptEncryptedPayload() {
        val encryptedDataKey = rawPreferences().all.keys.single { key ->
            !key.startsWith("__androidx_security_crypto_encrypted_prefs_")
        }
        check(rawPreferences().edit().putString(encryptedDataKey, "not-valid-ciphertext").commit())
    }

    private fun snapshot() = PatrolGridRemoteSnapshot(
        identity = PatrolGridIdentity(
            userId = "user-sensitive",
            subdivisionId = "subdivision-1",
            subdivisionName = "Central Subdivision",
            displayName = "Officer One",
            badgeNumber = "B-17",
            role = PatrolRole.SUPERVISOR,
        ),
        missions = listOf(
            PatrolMission(
                id = "mission-1",
                title = "Night perimeter",
                dutyWindow = "02 Sep · 22:00–02:00",
                unitName = "Unit 12",
                personnelCount = 4,
                status = PatrolMissionStatus.NEEDS_REVIEW,
                statusLabel = "Needs context",
                context = "Sensitive north gate instruction",
                priorityLocations = listOf(
                    PriorityLocation(
                        id = "priority-1",
                        name = "North gate",
                        state = PriorityLocationState.VISITED,
                        detail = "Visited",
                        required = true,
                    ),
                    PriorityLocation(
                        id = "priority-2",
                        name = "Canal bridge",
                        state = PriorityLocationState.REMAINING,
                        detail = "Remaining",
                        required = false,
                    ),
                ),
                lastUpdateLabel = "3m ago",
                hasOperationalDeviation = true,
                version = 9,
                endsAtEpochMs = 1_788_294_600_000L,
                retentionUntilEpochMs = 1_819_830_600_000L,
            ),
        ),
        evidenceMissionId = "mission-1",
        recordedTrackPoints = 42,
        observationCount = 3,
        routePoints = listOf(
            PatrolMapPoint(13.0, 77.5),
            PatrolMapPoint(13.01, 77.51),
        ),
        plannedRoutePoints = listOf(
            PatrolMapPoint(12.99, 77.49),
            PatrolMapPoint(13.02, 77.52),
        ),
        reviewContextRequestId = "review-request-9",
        reviewContextRequest = "Explain why the north gate was skipped",
        reviewContextResponse = "Road closure required a safe diversion",
        evidenceSources = listOf(
            PatrolEvidenceSource(
                sessionId = "session-sensitive-9",
                userId = "user-evidence",
                displayName = "Officer Evidence",
                badgeNumber = "B-29",
                startedAtMs = 1_788_280_200_000L,
                endedAtMs = 1_788_294_600_000L,
                endReason = "completed",
                appVersion = "1.4.0",
                trackPointCount = 42,
                firstRecordedAtMs = 1_788_280_210_000L,
                lastRecordedAtMs = 1_788_294_590_000L,
                firstReceivedAtMs = 1_788_280_211_000L,
                lastReceivedAtMs = 1_788_294_591_000L,
                bestAccuracyM = 3.5f,
                worstAccuracyM = 18.25f,
            ),
        ),
        selectedEvidenceSessionId = "session-sensitive-9",
        priorityVisitEvidence = listOf(
            PatrolPriorityVisitEvidence(
                sessionId = "session-sensitive-9",
                priorityLocationId = "priority-1",
                priorityName = "North gate",
                userId = "user-evidence",
                displayName = "Officer Evidence",
                visitedAtMs = 1_788_286_000_000L,
                receivedAtMs = 1_788_286_001_000L,
                method = "manual_with_context",
                accuracyM = null,
                note = "Manual visit because GPS was unavailable",
            ),
        ),
    )

    private companion object {
        const val FILE_NAME = "patrolgrid_mission_cache"
        const val KEY_SNAPSHOT = "authorized_snapshot"
        const val RETENTION_FILE = "patrolgrid_mission_retention"
        const val DAY_MS = 24L * 60L * 60L * 1_000L
    }
}
