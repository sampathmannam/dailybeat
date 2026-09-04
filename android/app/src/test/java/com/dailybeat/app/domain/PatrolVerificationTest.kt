package com.dailybeat.app.domain

import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PatrolReviewOutcome
import com.dailybeat.app.data.model.PatrolVerification
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import org.junit.Assert.assertEquals
import org.junit.Test

class PatrolVerificationTest {
    @Test
    fun activePatrolRemainsInProgress() {
        val review = PatrolVerification.evaluate(mission(PatrolMissionStatus.ACTIVE), recordedTrackPoints = 4)

        assertEquals(PatrolReviewOutcome.IN_PROGRESS, review.outcome)
    }

    @Test
    fun completedPatrolWithEvidenceIsReadyForHumanReview() {
        val review = PatrolVerification.evaluate(mission(PatrolMissionStatus.COMPLETED), recordedTrackPoints = 8)

        assertEquals(PatrolReviewOutcome.READY_FOR_REVIEW, review.outcome)
    }

    @Test
    fun deviationRequestsContextInsteadOfCreatingFailureScore() {
        val review = PatrolVerification.evaluate(
            mission(PatrolMissionStatus.COMPLETED).copy(hasOperationalDeviation = true),
            recordedTrackPoints = 8,
        )

        assertEquals(PatrolReviewOutcome.NEEDS_CONTEXT, review.outcome)
        assertEquals("Review the recorded operational reason", review.summary)
    }

    @Test
    fun missingRoutePointsRequestsDeviceOrNetworkContext() {
        val review = PatrolVerification.evaluate(mission(PatrolMissionStatus.COMPLETED), recordedTrackPoints = 0)

        assertEquals(PatrolReviewOutcome.NEEDS_CONTEXT, review.outcome)
    }

    private fun mission(status: PatrolMissionStatus) = PatrolMission(
        id = "mission-1",
        title = "Night patrol · Sector 4",
        dutyWindow = "22:00–02:00",
        unitName = "Unit 12",
        personnelCount = 4,
        status = status,
        statusLabel = "Ready for review",
        context = "",
        priorityLocations = listOf(
            PriorityLocation("p1", "Bus stand", PriorityLocationState.VISITED, "Visited"),
            PriorityLocation("p2", "Market junction", PriorityLocationState.VISITED, "Visited"),
        ),
        lastUpdateLabel = "Now",
    )
}
