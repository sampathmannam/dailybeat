package com.dailybeat.app.ui.insights

import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.ui.feed.RoutePoint
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeeklyDistanceQualityTest {
    private val empty = DayFeedItem(LocalDate.of(2026, 9, 25), emptyList(), emptyList(), 0.0,
        firstSeenMs = null, lastSeenMs = null, diaryPreview = null)
    private fun recorded(estimated: Boolean) = empty.copy(
        route = listOf(RoutePoint(11.45, 78.18, false)), distanceMeters = 1000.0,
        distanceEstimated = estimated,
    )

    @Test fun emptyAndFutureDaysDoNotMakeAnExactWeekEstimated() {
        assertFalse(InsightsUiState(weekDays = listOf(empty, recorded(false))).weeklyDistanceEstimated)
        assertFalse(InsightsUiState(weekDays = listOf(empty)).weeklyDistanceEstimated)
    }

    @Test fun anyEstimatedContributionMakesTheAggregateEstimated() {
        assertTrue(InsightsUiState(weekDays = listOf(recorded(false), recorded(true), empty))
            .weeklyDistanceEstimated)
    }

    @Test fun incompleteZeroDistanceEvidenceStillSignalsUncertainty() {
        assertTrue(InsightsUiState(weekDays = listOf(recorded(true).copy(distanceMeters = 0.0,
            captureGapCount = 1))).weeklyDistanceEstimated)
    }
}
