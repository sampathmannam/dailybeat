package com.dailybeat.app.patrolgrid

import com.dailybeat.app.backup.BackupSession
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption

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
    val recordedTrackPoints: Int,
    val observationCount: Int,
    val routePoints: List<PatrolMapPoint>,
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
    suspend fun startSession(missionId: String, installationId: String, appVersion: String): Result<String>
    suspend fun endSession(sessionId: String, reason: String = "completed"): Result<Unit>
    suspend fun uploadTrackPoints(
        missionId: String,
        sessionId: String,
        points: List<RemoteTrackPoint>,
    ): Result<Unit>
    suspend fun markPriorityVisited(
        missionId: String,
        priorityLocationId: String,
        clientVisitId: String,
        visitedAtMs: Long,
    ): Result<Unit>
    suspend fun addFieldUpdate(
        missionId: String,
        category: String,
        detail: String,
        clientUpdateId: String,
        occurredAtMs: Long,
    ): Result<Unit>
    fun signOut()
}
