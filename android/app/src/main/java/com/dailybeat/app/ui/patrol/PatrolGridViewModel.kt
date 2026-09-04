package com.dailybeat.app.ui.patrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.capture.PatrolCaptureStatus
import com.dailybeat.app.capture.PatrolEvidenceIncidentStatus
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolReview
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
import com.dailybeat.app.data.model.PatrolVerification
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import com.dailybeat.app.data.repo.PatrolGridRepository
import com.dailybeat.app.data.repo.PatrolRouteEvidence
import com.dailybeat.app.util.PermissionHelper
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker
import com.dailybeat.app.patrolgrid.PatrolMapPoint
import com.dailybeat.app.patrolgrid.PatrolEvidenceSource
import com.dailybeat.app.patrolgrid.PatrolPriorityVisitEvidence
import com.dailybeat.app.patrolgrid.PatrolGridAccessDeniedException
import com.dailybeat.app.patrolgrid.PatrolGridSessionExpiredException
import com.dailybeat.app.patrolgrid.isTransientPatrolGridFailure
import com.dailybeat.app.patrolgrid.PATROLGRID_RETENTION_ENFORCEMENT_ERROR
import com.dailybeat.app.patrolgrid.PATROLGRID_RETENTION_INCIDENT_MESSAGE
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.security.MapCachePrivacy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class PatrolRouteDisplayEvidence(
    val recordedTrackPoints: Int,
    val routePoints: List<PatrolMapPoint>,
    val unreadableTrackPoints: Int,
)

/**
 * Selects one internally coherent route snapshot.
 *
 * Room evidence wins when it is demonstrably newer, but a small device-local list must not
 * replace a fuller server trail simply because the local count changed. Keeping count, route,
 * and unreadable-point information from the same candidate also avoids misleading combinations
 * such as "100 points" beside a one-point line.
 */
internal fun selectRouteDisplayEvidence(
    snapshot: PatrolRouteDisplayEvidence,
    observed: PatrolRouteDisplayEvidence?,
    observedIsNewer: Boolean,
    protectFullerSnapshot: Boolean,
): PatrolRouteDisplayEvidence {
    observed ?: return snapshot
    if (protectFullerSnapshot && snapshot.routePoints.size > observed.routePoints.size) return snapshot
    return when {
        observed.recordedTrackPoints > snapshot.recordedTrackPoints -> observed
        observed.recordedTrackPoints < snapshot.recordedTrackPoints -> snapshot
        observed.routePoints.size > snapshot.routePoints.size -> observed
        observed.routePoints.size < snapshot.routePoints.size -> snapshot
        observed.unreadableTrackPoints < snapshot.unreadableTrackPoints -> observed
        observed.unreadableTrackPoints > snapshot.unreadableTrackPoints -> snapshot
        observedIsNewer -> observed
        else -> snapshot
    }
}

/** A matching terminal row is authoritative; omission is not, because snapshots are bounded. */
internal fun terminalMissionForActivePatrol(
    activeMissionId: String?,
    missions: List<PatrolMission>,
): PatrolMission? = activeMissionId?.let { missionId ->
    missions.firstOrNull { mission ->
        mission.id == missionId && (
            mission.status == PatrolMissionStatus.COMPLETED ||
                mission.status == PatrolMissionStatus.NEEDS_REVIEW
            )
    }
}

enum class PatrolSection { PRIMARY, MISSIONS, UNITS, MORE }
enum class SupervisorMissionTab { ACTIVE, NEEDS_REVIEW, UPCOMING }

data class PatrolGridUiState(
    val role: PatrolRole = PatrolRole.PATROL,
    val selectedSection: PatrolSection = PatrolSection.PRIMARY,
    val supervisorTab: SupervisorMissionTab = SupervisorMissionTab.ACTIVE,
    val primaryMission: PatrolMission? = null,
    val allMissions: List<PatrolMission> = emptyList(),
    val activeMissions: List<PatrolMission> = emptyList(),
    val upcomingMission: PatrolMission? = null,
    val selectedMissionId: String? = null,
    val evidenceMissionId: String? = null,
    val missionDetailsOpen: Boolean = false,
    val routePlans: List<PatrolRoutePlan> = emptyList(),
    val unitOptions: List<PatrolUnitOption> = emptyList(),
    val assignmentEditorOpen: Boolean = false,
    val recordedTrackPoints: Int = 0,
    val selectedEvidenceTrackPointCount: Int = 0,
    val unreadableTrackPoints: Int = 0,
    val captureError: String? = null,
    val trackingActive: Boolean = false,
    /**
     * A session this officer still has open on the server while this device is not tracking.
     * Surfaced so an officer whose device lost its local patrol state can take the patrol
     * back rather than being shown a mission that is "On route" and simultaneously "closed".
     */
    val resumableSessionId: String? = null,
    val resumableMissionId: String? = null,
    val locationPermissionGranted: Boolean = false,
    val observationCount: Int = 0,
    val review: PatrolReview? = null,
    val message: String? = null,
    val messageId: Long = 0,
    val loading: Boolean = false,
    val serverBacked: Boolean = false,
    val subdivisionName: String? = null,
    val operationInProgress: Boolean = false,
    val routePoints: List<PatrolMapPoint> = emptyList(),
    val plannedRoutePoints: List<PatrolMapPoint> = emptyList(),
    val reviewContextRequestId: String? = null,
    val reviewContextRequest: String? = null,
    val reviewContextResponse: String? = null,
    val evidenceSources: List<PatrolEvidenceSource> = emptyList(),
    val selectedEvidenceSessionId: String? = null,
    val priorityVisitEvidence: List<PatrolPriorityVisitEvidence> = emptyList(),
    val evidenceTrailLoading: Boolean = false,
    val evidenceTrailError: String? = null,
    val refreshError: String? = null,
    val showingCachedData: Boolean = false,
    val pendingActionCount: Int = 0,
    val pendingRoutePointCount: Int = 0,
    val pendingSessionClose: Boolean = false,
    val sessionExpired: Boolean = false,
)

class PatrolGridViewModel(application: Application) : AndroidViewModel(application) {
    private data class ObservedRouteEvidence(
        val missionId: String,
        val sessionId: String?,
        val evidence: PatrolRouteEvidence,
        val revision: Long,
    )

    private val app = application as DailyBeatApp
    private val repository: PatrolGridRepository = app.patrolGridRepository
    private var refreshGeneration = 0
    private var routeEvidenceRevision = 0L
    private var evidenceSelectionGeneration = 0L
    private var latestObservedRouteEvidence: ObservedRouteEvidence? = null
    private var observedRouteMissionId: String? = null
    private var observedRouteSessionId: String? = null
    private var routeObservationJob: Job? = null
    private val initialSettings = app.settingsRepository.get()
    private val _uiState = MutableStateFlow(
        PatrolGridUiState(
            role = initialSettings.patrolRole,
            captureError = patrolEvidenceWarning(initialSettings),
        ),
    )
    val uiState: StateFlow<PatrolGridUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            PatrolCaptureStatus.error.collect { error ->
                if (error != null) {
                    observeActiveRoute(null)
                    _uiState.update {
                        it.copy(
                            captureError = error,
                            trackingActive = false,
                        )
                    }
                    announce(error)
                    refresh(showLoading = false)
                }
            }
        }
        viewModelScope.launch {
            PatrolEvidenceIncidentStatus.error.collect { error ->
                if (error != null) {
                    _uiState.update {
                        it.copy(captureError = patrolEvidenceWarning(app.settingsRepository.get()))
                    }
                    announce(error)
                    refreshSyncStatus()
                }
            }
        }
        refresh()
    }

    fun selectSection(section: PatrolSection) {
        _uiState.update { it.copy(selectedSection = section) }
        if (app.isPatrolGridConfigured) refresh(showLoading = false)
    }

    fun selectSupervisorTab(tab: SupervisorMissionTab) {
        _uiState.update { it.copy(supervisorTab = tab) }
    }

    fun retryRefresh() = refresh()

    fun refreshFromForeground() = refresh(showLoading = false)

    fun openMission(missionId: String) {
        val mission = _uiState.value.allMissions.firstOrNull { it.id == missionId } ?: return
        ++evidenceSelectionGeneration
        _uiState.update {
            val switchingEvidence = it.evidenceMissionId != missionId
            val nextEvidenceMissionId = when {
                !app.isPatrolGridConfigured -> missionId
                switchingEvidence -> null
                else -> it.evidenceMissionId
            }
            it.copy(
                selectedMissionId = missionId,
                missionDetailsOpen = true,
                primaryMission = when {
                    it.role == PatrolRole.SUPERVISOR -> mission
                    !it.trackingActive && mission.status == PatrolMissionStatus.ASSIGNED -> mission
                    else -> it.primaryMission
                },
                evidenceMissionId = nextEvidenceMissionId,
                routePoints = if (switchingEvidence) emptyList() else it.routePoints,
                plannedRoutePoints = if (switchingEvidence) emptyList() else it.plannedRoutePoints,
                recordedTrackPoints = if (switchingEvidence) 0 else it.recordedTrackPoints,
                selectedEvidenceTrackPointCount = if (switchingEvidence) {
                    0
                } else {
                    it.selectedEvidenceTrackPointCount
                },
                observationCount = if (switchingEvidence) 0 else it.observationCount,
                reviewContextRequestId = if (switchingEvidence) null else it.reviewContextRequestId,
                reviewContextRequest = if (switchingEvidence) null else it.reviewContextRequest,
                reviewContextResponse = if (switchingEvidence) null else it.reviewContextResponse,
                evidenceSources = if (switchingEvidence) emptyList() else it.evidenceSources,
                selectedEvidenceSessionId = if (switchingEvidence) null else it.selectedEvidenceSessionId,
                priorityVisitEvidence = if (switchingEvidence) emptyList() else it.priorityVisitEvidence,
                evidenceTrailLoading = switchingEvidence && app.isPatrolGridConfigured,
                evidenceTrailError = null,
            )
        }
        if (app.isPatrolGridConfigured) refresh(showLoading = false, requestedMissionId = missionId)
    }

    fun dismissMissionDetails() {
        _uiState.update { it.copy(missionDetailsOpen = false) }
    }

    fun selectEvidenceSource(sessionId: String) {
        val current = _uiState.value
        if (!current.serverBacked || current.evidenceMissionId == null) return
        if (current.evidenceSources.none { it.sessionId == sessionId }) return
        if (current.selectedEvidenceSessionId == sessionId &&
            current.evidenceTrailError == null &&
            !current.evidenceTrailLoading
        ) {
            return
        }
        val selectionGeneration = ++evidenceSelectionGeneration
        val selectedSourceCount = current.evidenceSources
            .first { it.sessionId == sessionId }
            .trackPointCount
        _uiState.update {
            it.copy(
                selectedEvidenceSessionId = sessionId,
                selectedEvidenceTrackPointCount = selectedSourceCount,
                routePoints = emptyList(),
                evidenceTrailLoading = true,
                evidenceTrailError = null,
            )
        }
        viewModelScope.launch {
            app.patrolGridRemote.loadEvidenceTrail(sessionId).fold(
                onSuccess = success@{ trail ->
                    if (selectionGeneration != evidenceSelectionGeneration ||
                        _uiState.value.selectedEvidenceSessionId != sessionId
                    ) {
                        return@success
                    }
                    if (trail.sessionId != sessionId) {
                        _uiState.update {
                            it.copy(
                                evidenceTrailLoading = false,
                                evidenceTrailError = "The server returned mismatched route evidence. Try again.",
                            )
                        }
                        return@success
                    }
                    _uiState.update {
                        it.copy(
                            routePoints = trail.routePoints,
                            evidenceTrailLoading = false,
                            evidenceTrailError = null,
                        )
                    }
                },
                onFailure = failure@{ error ->
                    if (selectionGeneration != evidenceSelectionGeneration ||
                        _uiState.value.selectedEvidenceSessionId != sessionId
                    ) {
                        return@failure
                    }
                    if (error is PatrolGridSessionExpiredException ||
                        error is PatrolGridAccessDeniedException
                    ) {
                        handleRemoteFailure(error, "Route evidence could not be loaded securely")
                        return@failure
                    }
                    _uiState.update {
                        it.copy(
                            evidenceTrailLoading = false,
                            evidenceTrailError = "This route trail could not be loaded securely. Select it to retry.",
                        )
                    }
                },
            )
        }
    }

    fun refreshPermissionState() {
        val settings = app.settingsRepository.get()
        _uiState.update {
            it.copy(
                locationPermissionGranted = PermissionHelper.canCaptureLocation(app),
                captureError = patrolEvidenceWarning(settings),
            )
        }
    }

    fun setRole(role: PatrolRole) {
        if (!BuildConfig.DEBUG || app.isPatrolGridConfigured) return
        app.settingsRepository.setPatrolRole(role)
        _uiState.update {
            it.copy(role = role, selectedSection = PatrolSection.PRIMARY)
        }
        refresh()
        announce("Showing ${if (role == PatrolRole.SUPERVISOR) "supervisor" else "patrol personnel"} view")
    }

    /**
     * Take back a patrol whose session is still open on the server after this device lost its
     * local state -- a reinstall, cleared storage, or a replacement handset. Tracking still
     * only ever starts from an explicit tap, so the consent the privacy notice promises is
     * unchanged; this simply stops the officer being locked out of a patrol that is genuinely
     * running, unable to record evidence into it or to end it.
     */
    fun resumePatrol() {
        if (_uiState.value.operationInProgress) return
        val state = _uiState.value
        val sessionId = state.resumableSessionId ?: return
        val missionId = state.resumableMissionId ?: return
        val mission = state.allMissions.firstOrNull { it.id == missionId } ?: return
        val retention = app.settingsRepository.get()
        if (retention.patrolRetentionEnforcementFailureAtMs != null ||
            retention.patrolRetentionDeletionIntentCount > 0
        ) {
            announce("Patrol cannot resume until the required secure evidence cleanup succeeds")
            _uiState.update { it.copy(captureError = PATROLGRID_RETENTION_ENFORCEMENT_ERROR) }
            return
        }
        if (mission.endsAtEpochMs?.let { it <= System.currentTimeMillis() } == true) {
            announce("This duty window has ended. Ask the supervisor for a new or extended mission")
            return
        }
        if (!PermissionHelper.canCaptureLocation(app)) {
            announce("Location permission is required before patrol tracking can resume")
            refreshPermissionState()
            return
        }
        viewModelScope.launch {
            setOperationInProgress(true)
            app.settingsRepository.setPatrolEvidenceOwner(
                app.patrolGridRemote.currentSession()?.userId,
            )
            app.settingsRepository.setActivePatrolSession(sessionId)
            startLocalPatrol(missionId)
            setOperationInProgress(false)
            announce("Patrol resumed on this device; tracking is on again")
        }
    }

    fun startPatrol() {
        if (_uiState.value.operationInProgress) return
        val mission = _uiState.value.primaryMission ?: return
        val retention = app.settingsRepository.get()
        if (retention.patrolRetentionEnforcementFailureAtMs != null ||
            retention.patrolRetentionDeletionIntentCount > 0
        ) {
            announce("Patrol cannot start until the required secure evidence cleanup succeeds")
            _uiState.update { it.copy(captureError = PATROLGRID_RETENTION_ENFORCEMENT_ERROR) }
            return
        }
        if (mission.status != PatrolMissionStatus.ASSIGNED) {
            announce("Only an assigned mission can be started")
            return
        }
        if (mission.endsAtEpochMs?.let { it <= System.currentTimeMillis() } == true) {
            announce("This duty window has ended. Ask the supervisor for a new or extended mission")
            return
        }
        if (!PermissionHelper.canCaptureLocation(app)) {
            announce("Location permission is required before patrol tracking can start")
            refreshPermissionState()
            return
        }
        if (app.settingsRepository.get().pendingPatrolCloseSessionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
            announce("The previous patrol is still synchronizing. Try again when sync completes")
            return
        }
        if (!app.isPatrolGridConfigured) {
            startLocalPatrol(mission.id)
            return
        }
        viewModelScope.launch {
            setOperationInProgress(true)
            app.patrolGridRemote.startSession(
                missionId = mission.id,
                installationId = app.settingsRepository.installationId(),
                appVersion = BuildConfig.VERSION_NAME,
            ).fold(
                onSuccess = { sessionId ->
                    app.settingsRepository.setPatrolEvidenceOwner(
                        app.patrolGridRemote.currentSession()?.userId,
                    )
                    app.settingsRepository.setActivePatrolSession(sessionId)
                    startLocalPatrol(mission.id)
                },
                onFailure = { handleRemoteFailure(it, "Patrol could not be started securely") },
            )
            setOperationInProgress(false)
        }
    }

    fun markCurrentPriorityVisited() {
        if (app.isPatrolGridConfigured) {
            val mission = _uiState.value.primaryMission ?: return
            val priority = mission.priorityLocations.firstOrNull {
                it.state == com.dailybeat.app.data.model.PriorityLocationState.CURRENT
            } ?: return announce("All priority locations are already recorded")
            val sessionId = app.settingsRepository.get().activePatrolSessionId
                ?: return announce("No active patrol session was found")
            runCatching {
                app.patrolActionOutbox.enqueueVisit(mission.id, sessionId, priority.id)
            }.fold(
                onSuccess = {
                    markPriorityVisitedInState(priority.id)
                    PatrolTrackSyncWorker.enqueue(app)
                    refreshSyncStatus()
                    announce("${priority.name} saved securely; synchronization will continue when online")
                },
                onFailure = { announce(it.message ?: "Visit could not be saved securely") },
            )
            return
        }
        val name = repository.markCurrentPriorityVisited()
        announce(if (name == null) "All priority locations are already recorded" else "$name marked visited")
        refresh()
    }

    private fun markPriorityVisitedInState(priorityId: String) {
        _uiState.update { state ->
            val mission = state.primaryMission ?: return@update state
            val visitedIndex = mission.priorityLocations.indexOfFirst { it.id == priorityId }
            if (visitedIndex < 0) return@update state
            val updatedLocations = mission.priorityLocations.mapIndexed { index, location ->
                when {
                    index == visitedIndex -> location.copy(
                        state = com.dailybeat.app.data.model.PriorityLocationState.VISITED,
                        detail = "Saved · sync pending",
                    )
                    index == visitedIndex + 1 -> location.copy(
                        state = com.dailybeat.app.data.model.PriorityLocationState.CURRENT,
                        detail = "Current",
                    )
                    else -> location
                }
            }
            state.copy(primaryMission = mission.copy(priorityLocations = updatedLocations))
        }
    }

    fun addObservation(detail: String) {
        val normalized = detail.trim()
        if (normalized.isBlank()) return announce("Describe the observation before saving")
        if (app.isPatrolGridConfigured) {
            val mission = _uiState.value.primaryMission ?: return
            val sessionId = app.settingsRepository.get().activePatrolSessionId
                ?: return announce("No active patrol session was found")
            runCatching {
                app.patrolActionOutbox.enqueueUpdate(
                    mission.id,
                    sessionId,
                    "observation",
                    normalized,
                )
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(observationCount = it.observationCount + 1) }
                    PatrolTrackSyncWorker.enqueue(app)
                    refreshSyncStatus()
                    announce("Observation saved securely; synchronization will continue when online")
                },
                onFailure = { announce(it.message ?: "Observation could not be saved securely") },
            )
            return
        }
        val count = repository.addObservation()
        announce("Preview build · observation $count counted, the text is not stored")
        refresh()
    }

    fun recordDeviation(detail: String) {
        val normalized = detail.trim()
        if (normalized.isBlank()) return announce("Describe the operational reason before saving")
        if (app.isPatrolGridConfigured) {
            val mission = _uiState.value.primaryMission ?: return
            val sessionId = app.settingsRepository.get().activePatrolSessionId
                ?: return announce("No active patrol session was found")
            runCatching {
                app.patrolActionOutbox.enqueueUpdate(
                    mission.id,
                    sessionId,
                    "operational_deviation",
                    normalized,
                )
            }.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            primaryMission = state.primaryMission?.copy(
                                hasOperationalDeviation = true,
                                context = "Operational deviation saved · sync pending",
                            ),
                        )
                    }
                    PatrolTrackSyncWorker.enqueue(app)
                    refreshSyncStatus()
                    announce("Operational deviation saved securely for supervisor context")
                },
                onFailure = { announce(it.message ?: "Deviation could not be saved securely") },
            )
            return
        }
        repository.recordDeviation()
        announce("Preview build · deviation flagged, the reason text is not stored")
        refresh()
    }

    fun recordSafetyEvent(detail: String) {
        val normalized = detail.trim()
        if (normalized.isBlank()) return announce("Describe the safety event before saving")
        if (!app.isPatrolGridConfigured) {
            repository.addObservation()
            announce("Preview build · safety event counted, the text is not stored")
            refresh()
            return
        }
        val mission = _uiState.value.primaryMission ?: return
        val sessionId = app.settingsRepository.get().activePatrolSessionId
            ?: return announce("No active patrol session was found")
        runCatching {
            app.patrolActionOutbox.enqueueUpdate(mission.id, sessionId, "safety_event", normalized)
        }.fold(
            onSuccess = {
                PatrolTrackSyncWorker.enqueue(app)
                announce("Safety event saved securely for supervisor attention")
                refreshSyncStatus()
            },
            onFailure = { announce(it.message ?: "Safety event could not be saved securely") },
        )
    }

    fun recordReviewContext(detail: String) {
        val normalized = detail.trim()
        if (normalized.isBlank()) return announce("Add the requested context before saving")
        val state = _uiState.value
        val mission = state.primaryMission ?: return
        if (!app.isPatrolGridConfigured ||
            mission.status != PatrolMissionStatus.NEEDS_REVIEW ||
            state.reviewContextRequestId.isNullOrBlank() ||
            state.reviewContextRequest.isNullOrBlank()
        ) {
            announce("This mission does not have an open context request")
            return
        }
        val ownerId = app.patrolGridRemote.currentSession()?.userId
            ?: return handleRemoteFailure(
                PatrolGridSessionExpiredException(),
                "Your secure session ended before the context could be saved",
            )
        runCatching {
            app.patrolActionOutbox.enqueueUpdate(
                mission.id,
                null,
                "review_context",
                normalized,
                reviewId = requireNotNull(state.reviewContextRequestId),
            )
        }.fold(
            onSuccess = {
                app.settingsRepository.setPatrolEvidenceOwner(ownerId)
                _uiState.update { it.copy(reviewContextResponse = normalized) }
                PatrolTrackSyncWorker.enqueue(app)
                refreshSyncStatus()
                announce("Review context saved securely for the supervisor")
            },
            onFailure = { announce(it.message ?: "Review context could not be saved securely") },
        )
    }

    fun endPatrol(reason: PatrolEndReason) {
        if (_uiState.value.operationInProgress) return
        if (!app.isPatrolGridConfigured) {
            endLocalPatrol()
            return
        }
        val activePatrol = app.settingsRepository.get()
        if (activePatrol.activePatrolSessionId == null) {
            return announce("No active patrol session was found")
        }
        if (activePatrol.activePatrolMissionId == null) {
            return announce("No active patrol mission was found")
        }
        repository.endPatrol(pendingCloseReason = reason.storageValue)
        CaptureController.applyFromSettings(app)
        PatrolTrackSyncWorker.enqueue(app)
        refreshSyncStatus()
        announce("Patrol ended. Tracking is off; secure sync will finish when online")
        refresh()
    }

    fun assignPatrol() {
        if (_uiState.value.operationInProgress) return
        if (app.isPatrolGridConfigured &&
            (_uiState.value.routePlans.isEmpty() || _uiState.value.unitOptions.isEmpty())
        ) {
            announce("No active route and staffed unit are available for assignment")
            return
        }
        _uiState.update { it.copy(assignmentEditorOpen = true) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        if (_uiState.value.trackingActive) {
            announce("End the active patrol before signing out")
            return
        }
        if (app.settingsRepository.get().pendingPatrolCloseSessionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
            announce("Wait for the ended patrol to finish secure synchronization before signing out")
            return
        }
        if (app.isPatrolGridConfigured && app.patrolActionOutbox.pendingCount() > 0) {
            PatrolTrackSyncWorker.enqueue(app)
            announce("Wait for pending field updates to finish secure synchronization before signing out")
            return
        }
        if (app.isPatrolGridConfigured) {
            viewModelScope.launch {
                setOperationInProgress(true)
                app.patrolGridRemote.revokeSession()
                finishSignOut(onSignedOut)
            }
            return
        }
        app.patrolGridRemote.signOut()
        finishSignOut(onSignedOut)
    }

    fun lockApp(onLocked: () -> Unit) {
        if (!app.isPatrolGridConfigured) return
        clearSensitiveUiState()
        onLocked()
    }

    private fun finishSignOut(onSignedOut: () -> Unit) {
        if (app.isPatrolGridConfigured) app.patrolActionOutbox.clear()
        if (app.isPatrolGridConfigured) app.patrolGridSnapshotCache.clear()
        app.settingsRepository.setActivePatrolSession(null)
        app.settingsRepository.setActivePatrolMission(null)
        app.settingsRepository.setActivePatrolDeadline(null)
        app.settingsRepository.setPatrolEvidenceOwner(null)
        clearSensitiveUiState()
        onSignedOut()
    }

    fun dismissAssignment() {
        _uiState.update { it.copy(assignmentEditorOpen = false) }
    }

    fun saveAssignment(draft: PatrolAssignmentDraft) {
        if (app.isPatrolGridConfigured) {
            if (_uiState.value.operationInProgress) return
            viewModelScope.launch {
                setOperationInProgress(true)
                app.patrolGridRemote.createAssignment(draft).fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                assignmentEditorOpen = false,
                                supervisorTab = SupervisorMissionTab.UPCOMING,
                            )
                        }
                        announce("Patrol assigned and synchronized with the selected unit")
                        refresh()
                    },
                    onFailure = { handleRemoteFailure(it, "Patrol assignment could not be created") },
                )
                setOperationInProgress(false)
            }
            return
        }
        repository.assignPatrol(draft)
        _uiState.update {
            it.copy(
                assignmentEditorOpen = false,
                supervisorTab = SupervisorMissionTab.UPCOMING,
            )
        }
        announce("Patrol assigned. The route is ready for secure synchronization")
        refresh()
    }

    fun submitReview(outcome: SupervisorReviewOutcome, notes: String) {
        if (_uiState.value.operationInProgress) return
        val mission = _uiState.value.allMissions.firstOrNull {
            it.id == _uiState.value.selectedMissionId
        } ?: _uiState.value.primaryMission ?: return
        if (mission.status != PatrolMissionStatus.NEEDS_REVIEW) {
            announce("This mission is not ready for supervisor review")
            return
        }
        if (!app.isPatrolGridConfigured) {
            // Close the sheet first, exactly as the server-backed path below does. The
            // snackbar host lives on the Scaffold underneath the mission-detail sheet, so
            // announcing while the sheet is still open posts the confirmation where nobody
            // can see it and the submit looks like it did nothing.
            _uiState.update { it.copy(missionDetailsOpen = false) }
            announce(
                when (outcome) {
                    SupervisorReviewOutcome.APPROVED -> "Review approved in development preview"
                    SupervisorReviewOutcome.NEEDS_CONTEXT -> "More context requested in development preview"
                    SupervisorReviewOutcome.TECHNICALLY_INCONCLUSIVE -> "Review closed as technically inconclusive in development preview"
                },
            )
            return
        }
        viewModelScope.launch {
            setOperationInProgress(true)
            app.patrolGridRemote.submitReview(
                missionId = mission.id,
                expectedVersion = mission.version,
                outcome = outcome,
                notes = notes,
            ).fold(
                onSuccess = {
                    _uiState.update { it.copy(missionDetailsOpen = false) }
                    announce(
                        when (outcome) {
                            SupervisorReviewOutcome.APPROVED -> "Mission review approved"
                            SupervisorReviewOutcome.NEEDS_CONTEXT -> "Additional field context requested"
                            SupervisorReviewOutcome.TECHNICALLY_INCONCLUSIVE -> "Mission closed as technically inconclusive"
                        },
                    )
                    refresh(showLoading = false, requestedMissionId = mission.id)
                },
                onFailure = { error ->
                    handleRemoteFailure(error, "Mission review could not be submitted")
                },
            )
            setOperationInProgress(false)
        }
    }

    private fun refresh(
        showLoading: Boolean = true,
        requestedMissionId: String? = null,
    ) {
        val requestGeneration = ++refreshGeneration
        val routeRevisionAtStart = routeEvidenceRevision
        viewModelScope.launch {
            if (app.isPatrolGridConfigured) {
                refreshRemote(
                    showLoading = showLoading,
                    requestedMissionId = requestedMissionId,
                    requestGeneration = requestGeneration,
                    routeRevisionAtStart = routeRevisionAtStart,
                )
                return@launch
            }
            val snapshot = repository.snapshot()
            if (requestGeneration != refreshGeneration) return@launch
            val settings = app.settingsRepository.get()
            val routeEvidence = resolveRouteDisplayEvidence(
                missionId = snapshot.primaryMission.id,
                evidenceSessionId = null,
                snapshot = PatrolRouteDisplayEvidence(
                    recordedTrackPoints = snapshot.recordedTrackPoints,
                    routePoints = snapshot.routePoints,
                    unreadableTrackPoints = snapshot.unreadableTrackPoints,
                ),
                routeRevisionAtStart = routeRevisionAtStart,
                protectFullerSnapshot = false,
            )
            val allMissions = buildList {
                add(snapshot.primaryMission)
                addAll(snapshot.activeMissions)
                add(snapshot.upcomingMission)
            }.distinctBy { it.id }
            _uiState.update {
                it.copy(
                    primaryMission = snapshot.primaryMission,
                    allMissions = allMissions,
                    activeMissions = snapshot.activeMissions.filter { mission ->
                        mission.status == PatrolMissionStatus.ACTIVE ||
                            mission.status == PatrolMissionStatus.PAUSED_WITH_REASON
                    },
                    upcomingMission = snapshot.upcomingMission,
                    selectedMissionId = it.selectedMissionId ?: snapshot.primaryMission.id,
                    evidenceMissionId = snapshot.primaryMission.id,
                    recordedTrackPoints = routeEvidence.recordedTrackPoints,
                    selectedEvidenceTrackPointCount = routeEvidence.recordedTrackPoints,
                    unreadableTrackPoints = routeEvidence.unreadableTrackPoints,
                    captureError = patrolEvidenceWarning(settings),
                    trackingActive = snapshot.trackingActive,
                    locationPermissionGranted = PermissionHelper.canCaptureLocation(app),
                    observationCount = snapshot.observationCount,
                    review = PatrolVerification.evaluate(
                        snapshot.primaryMission,
                        routeEvidence.recordedTrackPoints,
                    ),
                    loading = false,
                    serverBacked = false,
                    routePlans = PatrolGridRepository.ROUTE_PLANS,
                    unitOptions = PatrolGridRepository.UNIT_OPTIONS,
                    routePoints = routeEvidence.routePoints,
                    plannedRoutePoints = emptyList(),
                    refreshError = null,
                )
            }
            observeActiveRoute(snapshot.primaryMission.id.takeIf { snapshot.trackingActive })
            refreshSyncStatus()
        }
    }

    private suspend fun refreshRemote(
        showLoading: Boolean,
        requestedMissionId: String?,
        requestGeneration: Int,
        routeRevisionAtStart: Long,
    ) {
        _uiState.update {
            it.copy(
                loading = showLoading,
                serverBacked = true,
                refreshError = if (showLoading) null else it.refreshError,
            )
        }
        val settings = app.settingsRepository.get()
        _uiState.update { it.copy(captureError = patrolEvidenceWarning(settings)) }
        val targetMissionId = when {
            settings.activePatrolMissionId != null -> settings.activePatrolMissionId
            requestedMissionId != null -> requestedMissionId
            else -> _uiState.value.selectedMissionId
        }
        app.patrolGridRemote.loadSnapshot(targetMissionId).fold(
            onSuccess = success@{ snapshot ->
                if (requestGeneration != refreshGeneration) return@success
                runCatching { app.patrolGridSnapshotCache.save(snapshot) }
                applyRemoteSnapshot(
                    snapshot = snapshot,
                    activeMissionId = settings.activePatrolMissionId,
                    requestedMissionId = targetMissionId,
                    cached = false,
                    error = null,
                    requestGeneration = requestGeneration,
                    routeRevisionAtStart = routeRevisionAtStart,
                )
            },
            onFailure = failure@{ error ->
                if (requestGeneration != refreshGeneration) return@failure
                if (error is PatrolGridSessionExpiredException || error is PatrolGridAccessDeniedException) {
                    stopTrackingForLostSession()
                    app.patrolGridRemote.signOut()
                    app.patrolGridSnapshotCache.clear()
                    clearSensitiveUiState(sessionExpired = true)
                    return@failure
                }
                val cached = if (error.isTransientPatrolGridFailure()) {
                    app.patrolGridRemote.currentSession()?.userId?.let {
                        app.patrolGridSnapshotCache.load(it)
                    }
                } else {
                    null
                }
                if (cached != null) {
                    applyRemoteSnapshot(
                        snapshot = cached,
                        activeMissionId = settings.activePatrolMissionId,
                        requestedMissionId = targetMissionId,
                        cached = true,
                        error = "Offline · showing the last securely cached briefing",
                        requestGeneration = requestGeneration,
                        routeRevisionAtStart = routeRevisionAtStart,
                    )
                } else {
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            serverBacked = true,
                            refreshError = error.message
                                ?: "PatrolGrid could not refresh. Check the network and try again",
                            showingCachedData = false,
                            routePlans = emptyList(),
                            unitOptions = emptyList(),
                        )
                    }
                }
            },
        )
        refreshSyncStatus()
    }

    private suspend fun applyRemoteSnapshot(
        snapshot: com.dailybeat.app.patrolgrid.PatrolGridRemoteSnapshot,
        activeMissionId: String?,
        requestedMissionId: String?,
        cached: Boolean,
        error: String?,
        requestGeneration: Int,
        routeRevisionAtStart: Long,
    ) {
        app.settingsRepository.setOfficerName(snapshot.identity.displayName)
        app.settingsRepository.setPatrolRole(snapshot.identity.role)
        val active = snapshot.missions.filter {
            it.status == PatrolMissionStatus.ACTIVE || it.status == PatrolMissionStatus.PAUSED_WITH_REASON
        }
        val assignmentOptionsResult = if (!cached && snapshot.identity.role == PatrolRole.SUPERVISOR) {
            app.patrolGridRemote.loadAssignmentOptions()
        } else {
            null
        }
        val assignmentOptions = assignmentOptionsResult?.getOrNull()
        val assignmentError = assignmentOptionsResult?.exceptionOrNull()?.message
        if (requestGeneration != refreshGeneration) return
        val currentActiveMissionId = app.settingsRepository.get().activePatrolMissionId
        if (currentActiveMissionId != null && currentActiveMissionId != activeMissionId) return
        val terminalActiveMission = terminalMissionForActivePatrol(
            activeMissionId = currentActiveMissionId,
            missions = snapshot.missions,
        )
        val effectiveActiveMissionId = if (terminalActiveMission != null) {
            stopTrackingForServerTerminalMission()
            null
        } else {
            currentActiveMissionId
        }
        val primaryId = effectiveActiveMissionId
            ?: requestedMissionId?.takeIf { snapshot.identity.role == PatrolRole.SUPERVISOR }
            ?: snapshot.evidenceMissionId
        val primary = primaryId?.let { id -> snapshot.missions.firstOrNull { it.id == id } }
            ?: snapshot.missions.firstOrNull()
        val upcoming = snapshot.missions.firstOrNull {
            it.status == PatrolMissionStatus.ASSIGNED && it.id != primary?.id
        }
        val selectedServerTrackPointCount = snapshot.evidenceSources
            .firstOrNull { it.sessionId == snapshot.selectedEvidenceSessionId }
            ?.trackPointCount
            ?: snapshot.routePoints.size
        val routeEvidence = resolveRouteDisplayEvidence(
            missionId = snapshot.evidenceMissionId,
            evidenceSessionId = snapshot.selectedEvidenceSessionId,
            snapshot = PatrolRouteDisplayEvidence(
                recordedTrackPoints = selectedServerTrackPointCount,
                routePoints = snapshot.routePoints,
                unreadableTrackPoints = 0,
            ),
            routeRevisionAtStart = routeRevisionAtStart,
            protectFullerSnapshot = true,
        )
        val localUnreadableTrackPoints = latestObservedRouteEvidence
            ?.takeIf { it.missionId == snapshot.evidenceMissionId }
            ?.evidence
            ?.unreadableTrackPoints
            ?: 0
        ++evidenceSelectionGeneration
        _uiState.update {
            it.copy(
                role = snapshot.identity.role,
                primaryMission = primary,
                allMissions = snapshot.missions,
                activeMissions = active,
                upcomingMission = upcoming,
                selectedMissionId = primary?.id,
                evidenceMissionId = snapshot.evidenceMissionId,
                recordedTrackPoints = snapshot.recordedTrackPoints,
                selectedEvidenceTrackPointCount = routeEvidence.recordedTrackPoints,
                unreadableTrackPoints = maxOf(
                    routeEvidence.unreadableTrackPoints,
                    localUnreadableTrackPoints,
                ),
                trackingActive = effectiveActiveMissionId != null,
                resumableSessionId = snapshot.resumableSessionId
                    ?.takeIf { effectiveActiveMissionId == null },
                resumableMissionId = snapshot.resumableMissionId
                    ?.takeIf { effectiveActiveMissionId == null },
                locationPermissionGranted = PermissionHelper.canCaptureLocation(app),
                observationCount = snapshot.observationCount,
                review = primary?.let { mission ->
                    PatrolVerification.evaluate(mission, snapshot.recordedTrackPoints)
                },
                loading = false,
                serverBacked = true,
                subdivisionName = snapshot.identity.subdivisionName,
                routePlans = assignmentOptions?.routes.orEmpty(),
                unitOptions = assignmentOptions?.units.orEmpty(),
                routePoints = routeEvidence.routePoints,
                plannedRoutePoints = snapshot.plannedRoutePoints,
                reviewContextRequestId = snapshot.reviewContextRequestId,
                reviewContextRequest = snapshot.reviewContextRequest,
                reviewContextResponse = snapshot.reviewContextResponse,
                evidenceSources = snapshot.evidenceSources,
                selectedEvidenceSessionId = snapshot.selectedEvidenceSessionId,
                priorityVisitEvidence = snapshot.priorityVisitEvidence,
                evidenceTrailLoading = false,
                evidenceTrailError = null,
                refreshError = error ?: assignmentError,
                showingCachedData = cached,
                sessionExpired = false,
                message = if (terminalActiveMission != null) {
                    "Patrol closed by the server. Tracking is off; secure sync will finish when online"
                } else {
                    it.message
                },
                messageId = if (terminalActiveMission != null) it.messageId + 1 else it.messageId,
            )
        }
        observeActiveRoute(effectiveActiveMissionId)
    }

    private fun resolveRouteDisplayEvidence(
        missionId: String?,
        evidenceSessionId: String?,
        snapshot: PatrolRouteDisplayEvidence,
        routeRevisionAtStart: Long,
        protectFullerSnapshot: Boolean,
    ): PatrolRouteDisplayEvidence {
        val observed = latestObservedRouteEvidence
            ?.takeIf {
                missionId != null &&
                    it.missionId == missionId &&
                    it.sessionId == evidenceSessionId
            }
        return selectRouteDisplayEvidence(
            snapshot = snapshot,
            observed = observed?.evidence?.toDisplayEvidence(),
            observedIsNewer = observed?.revision?.let { it > routeRevisionAtStart } == true,
            protectFullerSnapshot = protectFullerSnapshot,
        )
    }

    private fun observeActiveRoute(missionId: String?) {
        val sessionId = missionId?.let {
            app.settingsRepository.get().activePatrolSessionId
        }
        if (missionId == observedRouteMissionId &&
            sessionId == observedRouteSessionId &&
            routeObservationJob?.isActive == true
        ) {
            return
        }
        routeObservationJob?.cancel()
        routeObservationJob = null
        if (latestObservedRouteEvidence?.missionId != missionId ||
            latestObservedRouteEvidence?.sessionId != sessionId
        ) {
            latestObservedRouteEvidence = null
        }
        observedRouteMissionId = missionId
        observedRouteSessionId = sessionId
        if (missionId == null) return

        routeObservationJob = viewModelScope.launch {
            repository.observeRouteEvidence(missionId, sessionId).collect { evidence ->
                val activeSettings = app.settingsRepository.get()
                if (activeSettings.activePatrolMissionId != missionId ||
                    activeSettings.activePatrolSessionId != sessionId
                ) {
                    return@collect
                }
                val observed = ObservedRouteEvidence(
                    missionId = missionId,
                    sessionId = sessionId,
                    evidence = evidence,
                    revision = ++routeEvidenceRevision,
                )
                latestObservedRouteEvidence = observed
                _uiState.update { state ->
                    if (!state.trackingActive ||
                        state.primaryMission?.id != missionId ||
                        state.evidenceMissionId != missionId ||
                        (state.selectedEvidenceSessionId != null &&
                            state.selectedEvidenceSessionId != sessionId)
                    ) {
                        state
                    } else {
                        val routeEvidence = selectRouteDisplayEvidence(
                            snapshot = PatrolRouteDisplayEvidence(
                                recordedTrackPoints = state.selectedEvidenceTrackPointCount,
                                routePoints = state.routePoints,
                                unreadableTrackPoints = state.unreadableTrackPoints,
                            ),
                            observed = observed.evidence.toDisplayEvidence(),
                            observedIsNewer = true,
                            protectFullerSnapshot = state.serverBacked,
                        )
                        val reviewTrackPointCount = if (state.serverBacked) {
                            state.recordedTrackPoints
                        } else {
                            routeEvidence.recordedTrackPoints
                        }
                        state.copy(
                            recordedTrackPoints = reviewTrackPointCount,
                            selectedEvidenceTrackPointCount = routeEvidence.recordedTrackPoints,
                            unreadableTrackPoints = evidence.unreadableTrackPoints,
                            routePoints = routeEvidence.routePoints,
                            review = state.primaryMission.let { mission ->
                                PatrolVerification.evaluate(mission, reviewTrackPointCount)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Stops a detached keyed ViewModel from retaining or continuing to decrypt route evidence. */
    fun deactivateRouteObservation() {
        ++refreshGeneration // Ignore any network/database refresh that was already in flight.
        ++evidenceSelectionGeneration
        observeActiveRoute(null)
        latestObservedRouteEvidence = null
        _uiState.update {
            it.copy(
                routePoints = emptyList(),
                plannedRoutePoints = emptyList(),
                recordedTrackPoints = 0,
                selectedEvidenceTrackPointCount = 0,
                unreadableTrackPoints = 0,
                evidenceSources = emptyList(),
                selectedEvidenceSessionId = null,
                priorityVisitEvidence = emptyList(),
                evidenceTrailLoading = false,
                evidenceTrailError = null,
            )
        }
    }

    private fun startLocalPatrol(missionId: String) {
        val canRecordRoute = PermissionHelper.canCaptureLocation(app)
        app.settingsRepository.setPatrolCaptureError(null)
        PatrolCaptureStatus.clear()
        _uiState.update {
            it.copy(captureError = patrolEvidenceWarning(app.settingsRepository.get()))
        }
        app.settingsRepository.setActivePatrolDeadline(
            _uiState.value.primaryMission
                ?.takeIf { it.id == missionId }
                ?.endsAtEpochMs,
        )
        repository.startPatrol(missionId)
        CaptureController.applyFromSettings(app)
        val captureState = app.settingsRepository.get()
        if (captureState.activePatrolMissionId != missionId) {
            // The failure handler persists, announces, and refreshes this state via its flow.
            return
        }
        announce(
            if (canRecordRoute) {
                "Patrol started. Tracking is limited to this mission"
            } else {
                "Patrol started. Grant location permission to record the route"
            },
        )
        refresh()
    }

    private fun endLocalPatrol() {
        repository.endPatrol()
        CaptureController.applyFromSettings(app)
        announce("Patrol ended. Location tracking is off")
        refresh()
    }

    private fun refreshSyncStatus() {
        viewModelScope.launch {
            val settings = app.settingsRepository.get()
            _uiState.update {
                it.copy(
                    captureError = patrolEvidenceWarning(settings),
                    pendingActionCount = if (app.isPatrolGridConfigured) {
                        runCatching { app.patrolActionOutbox.pendingCount() }.getOrDefault(0)
                    } else {
                        0
                    },
                    pendingRoutePointCount = runCatching { app.db.patrolTracks().pendingCount() }.getOrDefault(0),
                    pendingSessionClose = settings.pendingPatrolCloseSessionId != null,
                )
            }
        }
    }

    fun acknowledgeRetentionIncident() {
        if (app.settingsRepository.acknowledgePatrolRetentionIncident()) {
            val settings = app.settingsRepository.get()
            _uiState.update { it.copy(captureError = patrolEvidenceWarning(settings)) }
            announce("Evidence-integrity notice acknowledged; the aggregate audit record remains on this device")
        } else {
            announce("The evidence-integrity notice could not be acknowledged securely")
        }
    }

    private fun handleRemoteFailure(error: Throwable, fallback: String) {
        if (error is PatrolGridSessionExpiredException || error is PatrolGridAccessDeniedException) {
            stopTrackingForLostSession()
            app.patrolGridRemote.signOut()
            app.patrolGridSnapshotCache.clear()
            clearSensitiveUiState(sessionExpired = true)
            return
        }
        announce(error.message ?: fallback)
    }

    private fun stopTrackingForLostSession() {
        val settings = app.settingsRepository.get()
        if (settings.activePatrolMissionId != null ||
            settings.activePatrolSessionId != null ||
            settings.gpsCaptureEnabled
        ) {
            val stopped = repository.endPatrol(
                pendingCloseReason = PatrolEndReason.DEVICE_ISSUE.storageValue,
            )
            CaptureController.applyFromSettings(app)
            if (stopped.sessionId != null && stopped.missionId != null) {
                PatrolTrackSyncWorker.enqueue(app)
            }
        }
    }

    private fun stopTrackingForServerTerminalMission() {
        val stopped = repository.endPatrol(
            pendingCloseReason = PatrolEndReason.DEVICE_ISSUE.storageValue,
        )
        observeActiveRoute(null)
        CaptureController.applyFromSettings(app)
        if (stopped.sessionId != null && stopped.missionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
        }
    }

    private fun clearSensitiveUiState(sessionExpired: Boolean = false) {
        deactivateRouteObservation()
        MapCachePrivacy.clear(app)
        _uiState.value = PatrolGridUiState(
            serverBacked = app.isPatrolGridConfigured,
            sessionExpired = sessionExpired,
        )
    }

    private fun setOperationInProgress(inProgress: Boolean) {
        _uiState.update { it.copy(operationInProgress = inProgress) }
    }

    private fun announce(message: String) {
        _uiState.update { it.copy(message = message, messageId = it.messageId + 1) }
    }
}

private fun PatrolRouteEvidence.toDisplayEvidence(): PatrolRouteDisplayEvidence =
    PatrolRouteDisplayEvidence(
        recordedTrackPoints = recordedTrackPoints,
        routePoints = routePoints,
        unreadableTrackPoints = unreadableTrackPoints,
    )

private fun patrolEvidenceWarning(settings: AppSettings): String? = when {
    settings.patrolRetentionEnforcementFailureAtMs != null ||
        settings.patrolRetentionDeletionIntentCount > 0 -> PATROLGRID_RETENTION_ENFORCEMENT_ERROR
    settings.patrolRetentionIncidentUnresolved -> PATROLGRID_RETENTION_INCIDENT_MESSAGE
    else -> settings.patrolCaptureError
}
