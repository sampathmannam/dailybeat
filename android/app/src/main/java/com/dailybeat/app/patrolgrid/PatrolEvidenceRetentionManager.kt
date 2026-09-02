package com.dailybeat.app.patrolgrid

import com.dailybeat.app.capture.PatrolEvidenceIncidentStatus
import com.dailybeat.app.data.db.PatrolTrackDao
import com.dailybeat.app.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val PATROLGRID_LOCAL_RETENTION_DAYS = 365
internal const val PATROLGRID_RETENTION_INCIDENT_MESSAGE =
    "Patrol evidence reached the 365-day device retention limit before synchronization. " +
        "The expired local copy was deleted. Report this evidence-integrity incident " +
        "immediately through your normal command, radio, or phone chain."
internal const val PATROLGRID_RETENTION_ENFORCEMENT_ERROR =
    "PatrolGrid could not verify the exact 365-day local evidence cleanup. Do not treat " +
        "synchronization as complete; report this device issue immediately through your " +
        "normal command, radio, or phone chain."

internal class PatrolMissionClockUnavailableException : IllegalStateException(
    "Local patrol evidence is waiting for its authoritative server retention clock.",
)

internal fun reportPatrolRetentionEnforcementFailure(
    settings: SettingsRepository,
    occurredAtMs: Long,
) {
    check(settings.recordPatrolRetentionEnforcementFailure(occurredAtMs.coerceAtLeast(1L))) {
        "Unable to persist the retention-enforcement failure."
    }
    PatrolEvidenceIncidentStatus.report(PATROLGRID_RETENTION_ENFORCEMENT_ERROR)
}

internal data class PatrolEvidenceRetentionReport(
    val discardedActionCount: Int,
    val discardedTrackPointCount: Int,
    val discardedSnapshotCount: Int,
    val pendingCloseCleared: Boolean,
    val evidenceOwnerCleared: Boolean,
) {
    val discardedItemCount: Int
        get() = discardedActionCount + discardedTrackPointCount + discardedSnapshotCount +
            if (pendingCloseCleared) 1 else 0

    val hasIncident: Boolean
        get() = discardedItemCount > 0
}

/**
 * Applies the device-side half of PatrolGrid's fixed evidence-retention policy.
 * It never sends identifiers or deleted evidence to telemetry: only an aggregate
 * discarded-item count and time are persisted locally.
 */
internal class PatrolEvidenceRetentionManager(
    private val trackDao: PatrolTrackDao,
    private val actionOutbox: PatrolActionOutbox,
    private val retentionStore: PatrolMissionRetentionStore,
    private val snapshotCache: PatrolGridSnapshotCache,
    private val settings: SettingsRepository,
    private val retentionDays: Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val enforcementMutex = Mutex()

    init {
        require(retentionDays == PATROLGRID_LOCAL_RETENTION_DAYS) {
            "PatrolGrid local retention must match the approved 365-day policy."
        }
    }

    suspend fun enforce(
        allowUnknownClockRecovery: Boolean = false,
    ): Result<PatrolEvidenceRetentionReport> = enforcementMutex.withLock {
        enforceLocked(allowUnknownClockRecovery)
    }

    /**
     * Only the server's explicit PostgreSQL P0002 evidence-destination response reaches
     * this path. Generic 400/404 responses remain retryable/non-destructive because they
     * can also mean validation failure or a stale API schema cache.
     */
    suspend fun discardUnavailableMission(missionId: String): Result<PatrolEvidenceRetentionReport> =
        enforcementMutex.withLock {
            try {
                require(missionId.isNotBlank())
                val nowMs = clock().also { require(it > 0L) }
                val missionIds = setOf(missionId)
                val pending = settings.get()
                val actionPlan = actionOutbox.inspectRetention(missionIds)
                val trackPlan = trackDao.countForMissions(missionIds.toList())
                val pendingCloseCleared = pending.pendingPatrolCloseMissionId == missionId
                val snapshotPlan = snapshotCache.protectedSnapshotCount()
                val planned = actionPlan.discardedCount + trackPlan + snapshotPlan +
                    if (pendingCloseCleared) 1 else 0
                if (planned > 0) {
                    check(settings.beginPatrolRetentionDeletion(planned, nowMs)) {
                        "Unable to journal unavailable mission cleanup."
                    }
                }
                val actions = actionOutbox.purgeForMissionDeadlines(missionIds)
                val tracks = trackDao.deleteForMissions(missionIds.toList())
                val discardedSnapshots = snapshotCache.discardProtectedSnapshot()
                if (pendingCloseCleared) settings.setPendingPatrolClose(null, null)
                retentionStore.removeMissionIds(missionIds)
                val report = PatrolEvidenceRetentionReport(
                    discardedActionCount = actions.discardedCount,
                    discardedTrackPointCount = tracks,
                    discardedSnapshotCount = discardedSnapshots,
                    pendingCloseCleared = pendingCloseCleared,
                    evidenceOwnerCleared = false,
                )
                val incidentCount = maxOf(
                    pending.patrolRetentionDeletionIntentCount,
                    report.discardedItemCount,
                )
                if (incidentCount > 0) {
                    check(settings.completePatrolRetentionDeletion(incidentCount, nowMs)) {
                        "Unable to record unavailable mission cleanup."
                    }
                    PatrolEvidenceIncidentStatus.report(PATROLGRID_RETENTION_INCIDENT_MESSAGE)
                } else {
                    check(settings.recordPatrolRetentionEnforcementSuccess())
                }
                Result.success(report)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runCatching { reportPatrolRetentionEnforcementFailure(settings, clock()) }
                Result.failure(error)
            }
        }

    private suspend fun enforceLocked(
        allowUnknownClockRecovery: Boolean,
    ): Result<PatrolEvidenceRetentionReport> = try {
        val nowMs = clock()
        require(nowMs > 0L) { "The device clock is unavailable." }
        val pending = settings.get()
        val pendingMissionId = pending.pendingPatrolCloseMissionId
        val dueMissionIds = retentionStore.dueMissionIds(nowMs)
        val knownMissionIds = retentionStore.knownMissionIds()
        val missionIdsWithoutDeadline = retentionStore.missionIdsWithoutDeadline()
        val actionPlan = actionOutbox.inspectRetention(dueMissionIds)
        val localMissionIds = (actionOutbox.inspectMissionIdsForRetention() -
            actionPlan.affectedMissionIds) +
            trackDao.accountOwnedMissionIds().toSet()
        val temporarilyExpectedUnknown = buildSet {
            if (pending.activePatrolDeadlineMs?.let { deadline ->
                    nowMs <= deadline.saturatingPlus(UNKNOWN_CLOCK_GRACE_MS)
                } == true
            ) {
                pending.activePatrolMissionId?.let(::add)
            }
            if (pending.pendingPatrolCloseEndedAtMs?.let { endedAt ->
                    nowMs <= endedAt.saturatingPlus(UNKNOWN_CLOCK_GRACE_MS)
                } == true
            ) {
                pending.pendingPatrolCloseMissionId?.let(::add)
            }
        }
        val orphanedUnknownMissionIds = localMissionIds.filterTo(mutableSetOf()) { missionId ->
            missionId !in knownMissionIds ||
                (missionId in missionIdsWithoutDeadline && missionId !in temporarilyExpectedUnknown)
        }
        if (orphanedUnknownMissionIds.isNotEmpty() && !allowUnknownClockRecovery) {
            throw PatrolMissionClockUnavailableException()
        }

        val trackPlanCount = if (dueMissionIds.isEmpty()) 0 else {
            trackDao.countForMissions(dueMissionIds.toList())
        }
        val snapshotPlanCount = snapshotCache.retentionDiscardCount(nowMs)

        val hasAnyPendingCloseState = pending.pendingPatrolCloseSessionId != null ||
            pendingMissionId != null || pending.pendingPatrolCloseEndedAtMs != null
        val pendingCloseMalformed = hasAnyPendingCloseState && (
                pending.pendingPatrolCloseSessionId == null ||
                pendingMissionId == null ||
                pending.pendingPatrolCloseEndedAtMs == null
            )
        val pendingCloseEvidenceDiscarded = pendingMissionId?.let { it in dueMissionIds } == true ||
            (pendingMissionId != null && pendingMissionId in actionPlan.affectedMissionIds)
        val pendingCloseCleared = hasAnyPendingCloseState && (
            pendingCloseMalformed ||
                pendingCloseEvidenceDiscarded ||
                actionPlan.unreadableContainerCount > 0
            )

        val plannedDiscardCount = actionPlan.discardedCount + trackPlanCount +
            snapshotPlanCount + if (pendingCloseCleared) 1 else 0
        val previousJournalCount = pending.patrolRetentionDeletionIntentCount
        if (plannedDiscardCount > 0) {
            check(settings.beginPatrolRetentionDeletion(plannedDiscardCount, nowMs)) {
                "Unable to journal patrol evidence cleanup."
            }
        }

        val actionResult = actionOutbox.purgeForMissionDeadlines(dueMissionIds)
        val discardedTrackPoints = trackDao.deleteForMissions(dueMissionIds.toList())
        val discardedSnapshots = snapshotCache.purgeForRetentionIfNeeded(nowMs)
        if (pendingCloseCleared) settings.setPendingPatrolClose(null, null)
        retentionStore.removeMissionIds(dueMissionIds)

        val current = settings.get()
        val canReleaseEvidenceOwner = current.patrolEvidenceOwnerId != null &&
            current.activePatrolMissionId == null &&
            current.activePatrolSessionId == null &&
            current.pendingPatrolCloseSessionId == null &&
            actionOutbox.pendingCount() == 0 &&
            trackDao.accountOwnedEvidenceCount() == 0
        if (canReleaseEvidenceOwner) settings.setPatrolEvidenceOwner(null)

        val report = PatrolEvidenceRetentionReport(
            discardedActionCount = actionResult.discardedCount,
            discardedTrackPointCount = discardedTrackPoints,
            discardedSnapshotCount = discardedSnapshots,
            pendingCloseCleared = pendingCloseCleared,
            evidenceOwnerCleared = canReleaseEvidenceOwner,
        )
        val incidentCount = maxOf(previousJournalCount, report.discardedItemCount)
        if (incidentCount > 0) {
            check(settings.completePatrolRetentionDeletion(incidentCount, nowMs)) {
                "Unable to record the local evidence-retention incident."
            }
            PatrolEvidenceIncidentStatus.report(PATROLGRID_RETENTION_INCIDENT_MESSAGE)
        } else {
            check(settings.recordPatrolRetentionEnforcementSuccess()) {
                "Unable to persist successful retention enforcement."
            }
        }
        Result.success(report)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val nowMs = runCatching(clock).getOrNull()?.takeIf { it > 0L } ?: 1L
        runCatching { reportPatrolRetentionEnforcementFailure(settings, nowMs) }
        Result.failure(error)
    }

    private fun Long.saturatingPlus(value: Long): Long =
        if (Long.MAX_VALUE - this < value) Long.MAX_VALUE else this + value

    private companion object {
        const val UNKNOWN_CLOCK_GRACE_MS = 24L * 60L * 60L * 1_000L
    }

}
