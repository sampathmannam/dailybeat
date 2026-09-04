package com.dailybeat.app.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.security.PatrolCoordinates
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PatrolGridRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: DailyBeatDb
    private lateinit var repository: PatrolGridRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("patrolgrid_missions", Context.MODE_PRIVATE)
            .edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, DailyBeatDb::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PatrolGridRepository(
            context = context,
            trackDao = db.patrolTracks(),
            settings = SettingsRepository(context),
            coordinateDecoder = { point ->
                PatrolCoordinates(
                    latitude = 12.0 + point.timestampMs / 10_000.0,
                    longitude = 77.0 + point.timestampMs / 10_000.0,
                    accuracyM = 5f,
                )
            },
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("patrolgrid_missions", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `local snapshot exposes decrypted mission route in chronological order`() = runBlocking {
        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 200)
        insert("another-mission", timestampMs = 150)
        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 100)

        val snapshot = repository.snapshot()

        assertEquals(2, snapshot.recordedTrackPoints)
        assertEquals(0, snapshot.unreadableTrackPoints)
        assertEquals(listOf(12.01, 12.02), snapshot.routePoints.map { it.latitude })
        assertEquals(listOf(77.01, 77.02), snapshot.routePoints.map { it.longitude })
    }

    @Test
    fun `alternate assigned mission remains active and renders its own evidence`() = runBlocking {
        val missionId = "upcoming-school-corridor"
        repository.startPatrol(missionId)
        insert(missionId, timestampMs = 400)

        val snapshot = repository.snapshot()

        assertEquals(missionId, snapshot.primaryMission.id)
        assertEquals(true, snapshot.trackingActive)
        assertEquals(1, snapshot.recordedTrackPoints)
        assertEquals(12.04, snapshot.routePoints.single().latitude, 0.000001)
    }

    @Test
    fun `unreadable encrypted evidence is counted instead of silently replaced`() = runBlocking {
        val repositoryWithFailure = PatrolGridRepository(
            context = context,
            trackDao = db.patrolTracks(),
            settings = SettingsRepository(context),
            coordinateDecoder = { point ->
                if (point.timestampMs == 200L) error("simulated keystore failure")
                PatrolCoordinates(12.0, 77.0, 5f)
            },
        )
        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 100)
        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 200)

        val evidence = repositoryWithFailure.routeEvidence(PatrolGridRepository.PRIMARY_MISSION_ID)

        assertEquals(2, evidence.recordedTrackPoints)
        assertEquals(1, evidence.routePoints.size)
        assertEquals(1, evidence.unreadableTrackPoints)
    }

    @Test
    fun `one live collector decrypts each visible row at most once`() = runBlocking {
        val calls = ConcurrentHashMap<Long, AtomicInteger>()
        val cachingRepository = PatrolGridRepository(
            context = context,
            trackDao = db.patrolTracks(),
            settings = SettingsRepository(context),
            coordinateDecoder = { point ->
                calls.computeIfAbsent(point.timestampMs) { AtomicInteger() }.incrementAndGet()
                PatrolCoordinates(12.0, 77.0, 5f)
            },
        )
        val sawOne = CompletableDeferred<Unit>()
        val sawTwo = CompletableDeferred<Unit>()
        val observation = launch {
            cachingRepository.observeRouteEvidence(PatrolGridRepository.PRIMARY_MISSION_ID)
                .collect { evidence ->
                    if (evidence.routePoints.size == 1) sawOne.complete(Unit)
                    if (evidence.routePoints.size == 2) sawTwo.complete(Unit)
                }
        }

        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 100)
        sawOne.await()
        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 200)
        sawTwo.await()
        observation.cancelAndJoin()

        assertEquals(1, calls.getValue(100L).get())
        assertEquals(1, calls.getValue(200L).get())
    }

    @Test
    fun `active route flow emits a newly recorded encrypted point without screen refresh`() = runBlocking {
        val evidence = async {
            repository.observeRouteEvidence(PatrolGridRepository.PRIMARY_MISSION_ID)
                .first { it.recordedTrackPoints == 1 && it.routePoints.size == 1 }
        }

        insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = 300)

        assertEquals(12.03, evidence.await().routePoints.single().latitude, 0.000001)
    }

    @Test
    fun `latest route query remains bounded and chronological`() = runBlocking {
        (1L..4L).forEach { insert(PatrolGridRepository.PRIMARY_MISSION_ID, timestampMs = it) }

        val latest = db.patrolTracks().latestForMission(
            missionId = PatrolGridRepository.PRIMARY_MISSION_ID,
            limit = 2,
        )

        assertEquals(listOf(3L, 4L), latest.map { it.timestampMs })
    }

    @Test
    fun `session route evidence never merges another session from the same mission`() = runBlocking {
        val missionId = PatrolGridRepository.PRIMARY_MISSION_ID
        insert(missionId, timestampMs = 100, sessionId = "session-one")
        insert(missionId, timestampMs = 200, sessionId = "session-two")
        insert(missionId, timestampMs = 300, sessionId = "session-one")

        val evidence = repository.routeEvidence(missionId, sessionId = "session-one")

        assertEquals(2, evidence.recordedTrackPoints)
        assertEquals(listOf(12.01, 12.03), evidence.routePoints.map { it.latitude })
    }

    private suspend fun insert(
        missionId: String,
        timestampMs: Long,
        sessionId: String? = null,
    ) {
        db.patrolTracks().insert(
            PatrolTrackPoint(
                missionId = missionId,
                timestampMs = timestampMs,
                encryptedPayload = byteArrayOf(1, 2, 3),
                sessionId = sessionId,
            ),
        )
    }
}
