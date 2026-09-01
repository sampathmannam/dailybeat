package com.dailybeat.app.ui.patrol

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
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

    fun setRole(role: PatrolRole) {
        app.settingsRepository.setPatrolRole(role)
        _uiState.update {
            it.copy(role = role, selectedSection = PatrolSection.PRIMARY)
        }
        refresh()
        announce("Showing ${if (role == PatrolRole.SUPERVISOR) "supervisor" else "patrol personnel"} view")
    }

    fun startPatrol() {
        val canRecordRoute = PermissionHelper.canCaptureLocation(app)
        repository.startPatrol()
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

    fun markCurrentPriorityVisited() {
        val name = repository.markCurrentPriorityVisited()
        announce(if (name == null) "All priority locations are already recorded" else "$name marked visited")
        refresh()
    }

    fun addObservation() {
        val count = repository.addObservation()
        announce("Observation saved on this device · $count total")
        refresh()
    }

    fun recordDeviation() {
        repository.recordDeviation()
        announce("Operational deviation recorded for supervisor context")
        refresh()
    }

    fun endPatrol() {
        repository.endPatrol()
        CaptureController.applyFromSettings(app)
        announce("Patrol ended. Location tracking is off")
        refresh()
    }

    fun assignPatrol() {
        _uiState.update { it.copy(assignmentEditorOpen = true) }
    }

    fun dismissAssignment() {
        _uiState.update { it.copy(assignmentEditorOpen = false) }
    }

    fun saveAssignment(draft: PatrolAssignmentDraft) {
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
                )
            }
        }
    }

    private fun announce(message: String) {
        _uiState.update { it.copy(message = message, messageId = it.messageId + 1) }
    }
}
