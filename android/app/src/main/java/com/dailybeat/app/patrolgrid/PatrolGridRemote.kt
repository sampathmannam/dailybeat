package com.dailybeat.app.patrolgrid

import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import java.io.IOException

data class PatrolGridIdentity(
    val userId: String,
    val subdivisionId: String,
    val subdivisionName: String,
    val displayName: String,
    val badgeNumber: String?,
    val role: PatrolRole,
)

data class PatrolGridRemoteSnapshot(
    val identity: PatrolGridIdentity,
    val missions: List<PatrolMission>,
    val evidenceMissionId: String?,
    val recordedTrackPoints: Int,
    val observationCount: Int,
    val routePoints: List<PatrolMapPoint>,
    val plannedRoutePoints: List<PatrolMapPoint> = emptyList(),
    val reviewContextRequestId: String? = null,
    val reviewContextRequest: String? = null,
    val reviewContextResponse: String? = null,
)

data class PatrolMapPoint(val latitude: Double, val longitude: Double)

data class PatrolAssignmentOptions(
    val routes: List<PatrolRoutePlan>,
    val units: List<PatrolUnitOption>,
)

data class RemoteTrackPoint(
    val clientPointId: String,
    val sequenceNumber: Int,
    val recordedAtMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
)

interface PatrolGridRemote {
    val isConfigured: Boolean
    fun currentSession(): BackupSession?
    suspend fun signIn(email: String, password: String): Result<PatrolGridIdentity>
    suspend fun loadIdentity(): Result<PatrolGridIdentity>
    suspend fun loadSnapshot(activeMissionId: String?): Result<PatrolGridRemoteSnapshot>
    suspend fun loadAssignmentOptions(): Result<PatrolAssignmentOptions>
    suspend fun createAssignment(draft: PatrolAssignmentDraft): Result<Unit>
    suspend fun submitReview(
        missionId: String,
        expectedVersion: Int,
        outcome: SupervisorReviewOutcome,
        notes: String,
    ): Result<Int>
    suspend fun startSession(missionId: String, installationId: String, appVersion: String): Result<String>
    suspend fun endSession(
        sessionId: String,
        reason: String = "completed",
        endedAtMs: Long = System.currentTimeMillis(),
    ): Result<Unit>
    suspend fun uploadTrackPoints(
        missionId: String,
        sessionId: String,
        points: List<RemoteTrackPoint>,
    ): Result<Unit>
    suspend fun markPriorityVisited(
        sessionId: String,
        priorityLocationId: String,
        clientVisitId: String,
        visitedAtMs: Long,
    ): Result<Unit>
    suspend fun addFieldUpdate(
        sessionId: String?,
        category: String,
        detail: String,
        clientUpdateId: String,
        occurredAtMs: Long,
        reviewId: String? = null,
    ): Result<Unit>
    suspend fun revokeSession(): Result<Unit> {
        signOut()
        return Result.success(Unit)
    }
    fun signOut()
}

open class PatrolGridRemoteException(message: String) : IllegalStateException(message)

class PatrolGridSessionExpiredException :
    PatrolGridRemoteException("Your PatrolGrid session expired. Sign in again.")

class PatrolGridAccessDeniedException(
    message: String = "Your account is not authorized for this PatrolGrid action.",
) : PatrolGridRemoteException(message)

class PatrolGridTransientException(message: String) : PatrolGridRemoteException(message)

/** Non-retryable evidence destination: the server no longer accepts this mission. */
class PatrolGridEvidenceUnavailableException :
    PatrolGridRemoteException("The server no longer accepts evidence for this mission.")

internal class PatrolEvidenceDestinationUnavailableException(
    val missionId: String,
    cause: PatrolGridEvidenceUnavailableException,
) : IllegalStateException(cause.message, cause)

fun Throwable.isTransientPatrolGridFailure(): Boolean =
    this is IOException || this is PatrolGridTransientException
