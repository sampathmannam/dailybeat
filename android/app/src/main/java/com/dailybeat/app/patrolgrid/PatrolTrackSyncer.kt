package com.dailybeat.app.patrolgrid

import com.dailybeat.app.data.db.PatrolTrackDao
import com.dailybeat.app.data.settings.SettingsRepository
import com.dailybeat.app.security.PatrolTrackCipher
import com.dailybeat.app.data.model.PatrolTrackPoint
import com.dailybeat.app.security.PatrolCoordinates

class PatrolTrackSyncer(
    private val dao: PatrolTrackDao,
    private val cipher: PatrolTrackCipher,
    private val remote: PatrolGridRemote,
    private val settings: SettingsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val coordinateDecoder: (PatrolTrackPoint) -> PatrolCoordinates = { point ->
        cipher.decrypt(
            missionId = point.missionId,
            timestampMs = point.timestampMs,
            encryptedPayload = point.encryptedPayload,
        )
    },
) {
    suspend fun syncPending(): Result<Int> = runCatching {
        if (!remote.isConfigured || remote.currentSession() == null) return@runCatching 0
        var uploaded = 0
        repeat(MAX_BATCHES_PER_RUN) {
            val pending = dao.pending(limit = BATCH_SIZE)
            if (pending.isEmpty()) return@repeat
            val groups = pending.groupBy { requireNotNull(it.sessionId) to it.missionId }
            groups.forEach { (key, points) ->
                val (sessionId, missionId) = key
                val payload = points.map { point ->
                    val coordinates = coordinateDecoder(point)
                    RemoteTrackPoint(
                        clientPointId = requireNotNull(point.clientPointId),
                        sequenceNumber = Math.toIntExact(point.id),
                        recordedAtMs = point.timestampMs,
                        latitude = coordinates.latitude,
                        longitude = coordinates.longitude,
                        accuracyM = coordinates.accuracyM,
                    )
                }
                try {
                    remote.uploadTrackPoints(missionId, sessionId, payload).getOrThrow()
                } catch (error: PatrolGridEvidenceUnavailableException) {
                    throw PatrolEvidenceDestinationUnavailableException(missionId, error)
                }
                dao.markSynced(points.map { it.id }, clock())
                uploaded += points.size
            }
            if (pending.size < BATCH_SIZE) return@runCatching uploaded
        }
        uploaded
    }

    suspend fun syncAndClosePendingSession(): Result<Int> = runCatching {
        val uploaded = syncPending().getOrThrow()
        val pending = settings.get()
        val sessionId = pending.pendingPatrolCloseSessionId
        val missionId = pending.pendingPatrolCloseMissionId
        if (sessionId != null && missionId != null) {
            val remaining = dao.pending(missionId = missionId, limit = 1)
            check(remaining.isEmpty()) { "Route evidence is still waiting to synchronize." }
            try {
                remote.endSession(
                    sessionId = sessionId,
                    reason = pending.pendingPatrolCloseReason,
                    endedAtMs = pending.pendingPatrolCloseEndedAtMs ?: clock(),
                ).getOrThrow()
            } catch (error: PatrolGridEvidenceUnavailableException) {
                throw PatrolEvidenceDestinationUnavailableException(missionId, error)
            }
            dao.deleteForMission(missionId)
            settings.setPendingPatrolClose(null, null)
            settings.setPatrolEvidenceOwner(null)
        }
        uploaded
    }

    private companion object {
        const val BATCH_SIZE = 250
        const val MAX_BATCHES_PER_RUN = 8
    }
}
