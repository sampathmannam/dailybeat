package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PatrolRole(val storageValue: String) {
    SUPERVISOR("supervisor"),
    PATROL("patrol");

    companion object {
        fun fromStorage(value: String?): PatrolRole =
            entries.firstOrNull { it.storageValue == value } ?: PATROL
    }
}

enum class PatrolRouteGuidance { SUGGESTED_ROUTE, AREA_COVERAGE }

data class PatrolRoutePlan(
    val id: String,
    val title: String,
    val dutyWindow: String,
    val priorityLocations: List<String>,
)

data class PatrolUnitOption(
    val name: String,
    val personnelCount: Int,
)

data class PatrolAssignmentDraft(
    val routePlanId: String,
    val unitName: String,
    val personnelCount: Int,
    val guidance: PatrolRouteGuidance,
)

enum class PatrolMissionStatus {
    ASSIGNED,
    ACTIVE,
    PAUSED_WITH_REASON,
    COMPLETED,
    NEEDS_REVIEW,
}

enum class PriorityLocationState {
    VISITED,
    CURRENT,
    REMAINING,
}

data class PriorityLocation(
    val id: String,
    val name: String,
    val state: PriorityLocationState,
    val detail: String,
    val required: Boolean = true,
)

data class PatrolMission(
    val id: String,
    val title: String,
    val dutyWindow: String,
    val unitName: String,
    val personnelCount: Int,
    val status: PatrolMissionStatus,
    val statusLabel: String,
    val context: String,
    val priorityLocations: List<PriorityLocation>,
    val lastUpdateLabel: String,
    val hasOperationalDeviation: Boolean = false,
)

enum class PatrolReviewOutcome {
    IN_PROGRESS,
    READY_FOR_REVIEW,
    NEEDS_CONTEXT,
}

data class PatrolReview(
    val outcome: PatrolReviewOutcome,
    val summary: String,
)

object PatrolVerification {
    /**
     * Produces a review signal, not a staff score. GPS gaps and deviations always
     * remain contextual for a supervisor to review with the patrol team.
     */
    fun evaluate(mission: PatrolMission, recordedTrackPoints: Int): PatrolReview {
        if (mission.status == PatrolMissionStatus.ACTIVE) {
            return PatrolReview(PatrolReviewOutcome.IN_PROGRESS, "Patrol is in progress")
        }
        val requiredVisited = mission.priorityLocations
            .filter { it.required }
            .all { it.state == PriorityLocationState.VISITED }
        return when {
            mission.hasOperationalDeviation -> PatrolReview(
                PatrolReviewOutcome.NEEDS_CONTEXT,
                "Review the recorded operational reason",
            )
            !requiredVisited -> PatrolReview(
                PatrolReviewOutcome.NEEDS_CONTEXT,
                "One or more priority locations need context",
            )
            recordedTrackPoints == 0 -> PatrolReview(
                PatrolReviewOutcome.NEEDS_CONTEXT,
                "No route points were available; check device or network context",
            )
            else -> PatrolReview(
                PatrolReviewOutcome.READY_FOR_REVIEW,
                "Route and priority locations are ready for supervisor review",
            )
        }
    }
}

@Entity(
    tableName = "patrol_track_points",
    indices = [Index(value = ["missionId", "timestampMs"])],
)
data class PatrolTrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val timestampMs: Long,
    val encryptedPayload: ByteArray,
)
