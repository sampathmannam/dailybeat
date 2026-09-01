package com.dailybeat.app.ui.patrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolReview
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption
import com.dailybeat.app.data.model.PatrolVerification
import com.dailybeat.app.data.repo.PatrolGridRepository
import com.dailybeat.app.util.PermissionHelper
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker
import com.dailybeat.app.patrolgrid.PatrolMapPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PatrolSection { PRIMARY, MISSIONS, UNITS, ALERTS, MESSAGES, MORE }
enum class SupervisorMissionTab { ACTIVE, NEEDS_REVIEW, UPCOMING }

data class PatrolGridUiState(
    val role: PatrolRole = PatrolRole.PATROL,
    val selectedSection: PatrolSection = PatrolSection.PRIMARY,
    val supervisorTab: SupervisorMissionTab = SupervisorMissionTab.ACTIVE,
    val primaryMission: PatrolMission? = null,
    val activeMissions: List<PatrolMission> = emptyList(),
    val upcomingMission: PatrolMission? = null,
    val routePlans: List<PatrolRoutePlan> = PatrolGridRepository.ROUTE_PLANS,
    val unitOptions: List<PatrolUnitOption> = PatrolGridRepository.UNIT_OPTIONS,
    val assignmentEditorOpen: Boolean = false,
    val recordedTrackPoints: Int = 0,
    val trackingActive: Boolean = false,
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
    val refreshError: String? = null,
    val showingCachedData: Boolean = false,
)

class PatrolGridViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    private val repository: PatrolGridRepository = app.patrolGridRepository
    private val _uiState = MutableStateFlow(
        PatrolGridUiState(role = app.settingsRepository.get().patrolRole),
    )
    val uiState: StateFlow<PatrolGridUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun selectSection(section: PatrolSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun selectSupervisorTab(tab: SupervisorMissionTab) {
        _uiState.update { it.copy(supervisorTab = tab) }
    }

    fun retryRefresh() = refresh()

    fun setRole(role: PatrolRole) {
        if (!BuildConfig.DEBUG || app.isPatrolGridConfigured) return
        app.settingsRepository.setPatrolRole(role)
        _uiState.update {
            it.copy(role = role, selectedSection = PatrolSection.PRIMARY)
        }
        refresh()
        announce("Showing ${if (role == PatrolRole.SUPERVISOR) "supervisor" else "patrol personnel"} view")
    }

    fun startPatrol() {
        if (_uiState.value.operationInProgress) return
        val mission = _uiState.value.primaryMission ?: return
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
                    app.settingsRepository.setActivePatrolSession(sessionId)
                    startLocalPatrol(mission.id)
                },
                onFailure = { announce(it.message ?: "Patrol could not be started securely") },
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
            runCatching { app.patrolActionOutbox.enqueueVisit(mission.id, priority.id) }.fold(
                onSuccess = {
                    markPriorityVisitedInState(priority.id)
                    PatrolTrackSyncWorker.enqueue(app)
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

    fun addObservation() {
        if (app.isPatrolGridConfigured) {
            val mission = _uiState.value.primaryMission ?: return
            runCatching {
                app.patrolActionOutbox.enqueueUpdate(
                    mission.id,
                    "observation",
                    "Observation recorded by patrol personnel",
                )
            }.fold(
                onSuccess = {
                    _uiState.update { it.copy(observationCount = it.observationCount + 1) }
                    PatrolTrackSyncWorker.enqueue(app)
                    announce("Observation saved securely; synchronization will continue when online")
                },
                onFailure = { announce(it.message ?: "Observation could not be saved securely") },
            )
            return
        }
        val count = repository.addObservation()
        announce("Observation saved on this device · $count total")
        refresh()
    }

    fun recordDeviation() {
        if (app.isPatrolGridConfigured) {
            val mission = _uiState.value.primaryMission ?: return
            runCatching {
                app.patrolActionOutbox.enqueueUpdate(
                    mission.id,
                    "operational_deviation",
                    "Operational deviation recorded; supervisor context required",
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
                    announce("Operational deviation saved securely for supervisor context")
                },
                onFailure = { announce(it.message ?: "Deviation could not be saved securely") },
            )
            return
        }
        repository.recordDeviation()
        announce("Operational deviation recorded for supervisor context")
        refresh()
    }

    fun endPatrol() {
        if (_uiState.value.operationInProgress) return
        if (!app.isPatrolGridConfigured) {
            endLocalPatrol()
            return
        }
        val sessionId = app.settingsRepository.get().activePatrolSessionId
            ?: return announce("No active patrol session was found")
        val missionId = app.settingsRepository.get().activePatrolMissionId
            ?: return announce("No active patrol mission was found")
        repository.endPatrol()
        app.settingsRepository.setPendingPatrolClose(sessionId, missionId)
        CaptureController.applyFromSettings(app)
        PatrolTrackSyncWorker.enqueue(app)
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
        app.patrolGridRemote.signOut()
        if (app.isPatrolGridConfigured) app.patrolActionOutbox.clear()
        if (app.isPatrolGridConfigured) app.patrolGridSnapshotCache.clear()
        app.settingsRepository.setActivePatrolSession(null)
        app.settingsRepository.setActivePatrolMission(null)
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
                    onFailure = { announce(it.message ?: "Patrol assignment could not be created") },
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

    private fun refresh() {
        viewModelScope.launch {
            if (app.isPatrolGridConfigured) {
                refreshRemote()
                return@launch
            }
            val snapshot = repository.snapshot()
            _uiState.update {
                it.copy(
                    primaryMission = snapshot.primaryMission,
                    activeMissions = snapshot.activeMissions,
                    upcomingMission = snapshot.upcomingMission,
                    recordedTrackPoints = snapshot.recordedTrackPoints,
                    trackingActive = snapshot.trackingActive,
                    locationPermissionGranted = PermissionHelper.canCaptureLocation(app),
                    observationCount = snapshot.observationCount,
                    review = PatrolVerification.evaluate(
                        snapshot.primaryMission,
                        snapshot.recordedTrackPoints,
                    ),
                    loading = false,
                    serverBacked = false,
                )
            }
        }
    }

    private suspend fun refreshRemote() {
        _uiState.update { it.copy(loading = true, serverBacked = true, refreshError = null) }
        val settings = app.settingsRepository.get()
        app.patrolGridRemote.loadSnapshot(settings.activePatrolMissionId).fold(
            onSuccess = { snapshot ->
                runCatching { app.patrolGridSnapshotCache.save(snapshot) }
                applyRemoteSnapshot(snapshot, settings.activePatrolMissionId, cached = false, error = null)
            },
            onFailure = { error ->
                val cached = app.patrolGridRemote.currentSession()?.userId?.let {
                    app.patrolGridSnapshotCache.load(it)
                }
                if (cached != null) {
                    applyRemoteSnapshot(
                        cached,
                        settings.activePatrolMissionId,
                        cached = true,
                        error = "Offline · showing the last securely cached briefing",
                    )
                } else {
                    _uiState.update { state ->
                        state.copy(
                            loading = false,
                            serverBacked = true,
                            refreshError = error.message
                                ?: "PatrolGrid could not refresh. Check the network and try again",
                            showingCachedData = false,
                        )
                    }
                }
            },
        )
    }

    private suspend fun applyRemoteSnapshot(
        snapshot: com.dailybeat.app.patrolgrid.PatrolGridRemoteSnapshot,
        activeMissionId: String?,
        cached: Boolean,
        error: String?,
    ) {
        app.settingsRepository.setOfficerName(snapshot.identity.displayName)
        app.settingsRepository.setPatrolRole(snapshot.identity.role)
        val primary = activeMissionId?.let { activeId ->
            snapshot.missions.firstOrNull { it.id == activeId }
        } ?: snapshot.missions.firstOrNull()
        val active = snapshot.missions.filter {
            it.status == com.dailybeat.app.data.model.PatrolMissionStatus.ACTIVE ||
                it.status == com.dailybeat.app.data.model.PatrolMissionStatus.PAUSED_WITH_REASON ||
                it.status == com.dailybeat.app.data.model.PatrolMissionStatus.ASSIGNED
        }
        val upcoming = snapshot.missions.firstOrNull {
            it.status == com.dailybeat.app.data.model.PatrolMissionStatus.ASSIGNED && it.id != primary?.id
        }
        val assignmentOptions = if (!cached && snapshot.identity.role == PatrolRole.SUPERVISOR) {
            app.patrolGridRemote.loadAssignmentOptions().getOrNull()
        } else {
            null
        }
        _uiState.update {
            it.copy(
                role = snapshot.identity.role,
                primaryMission = primary,
                activeMissions = active,
                upcomingMission = upcoming,
                recordedTrackPoints = snapshot.recordedTrackPoints,
                trackingActive = activeMissionId != null,
                locationPermissionGranted = PermissionHelper.canCaptureLocation(app),
                observationCount = snapshot.observationCount,
                review = primary?.let { mission ->
                    PatrolVerification.evaluate(mission, snapshot.recordedTrackPoints)
                },
                loading = false,
                serverBacked = true,
                subdivisionName = snapshot.identity.subdivisionName,
                routePlans = assignmentOptions?.routes ?: if (cached) emptyList() else it.routePlans,
                unitOptions = assignmentOptions?.units ?: if (cached) emptyList() else it.unitOptions,
                routePoints = snapshot.routePoints,
                refreshError = error,
                showingCachedData = cached,
            )
        }
    }

    private fun startLocalPatrol(missionId: String) {
        val canRecordRoute = PermissionHelper.canCaptureLocation(app)
        repository.startPatrol(missionId)
        CaptureController.applyFromSettings(app)
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

    private fun setOperationInProgress(inProgress: Boolean) {
        _uiState.update { it.copy(operationInProgress = inProgress) }
    }

    private fun announce(message: String) {
        _uiState.update { it.copy(message = message, messageId = it.messageId + 1) }
    }
}
