package com.dailybeat.app.ui.insights

import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.ui.feed.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ActionableInsightTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 10)

    @Test
    fun `today's unfinished Beat is the first review action`() {
        val action = actionableInsightFor(today, 0, beat(today), 0)

        assertEquals("Close today’s loop", action.title)
        assertEquals(InsightAction.OPEN_DAY, action.action)
        assertEquals(today, action.date)
    }

    @Test
    fun `an older unfinished Beat opens that date instead of a completed today`() {
        val yesterday = today.minusDays(1)

        val action = actionableInsightFor(today, 0, beat(yesterday), 1)

        assertEquals("Review your latest open Beat", action.title)
        assertEquals(InsightAction.OPEN_DAY, action.action)
        assertEquals(yesterday, action.date)
    }

    @Test
    fun `capture gaps take priority and open recovery settings`() {
        val action = actionableInsightFor(today, 2, beat(today), 0)

        assertEquals(InsightAction.OPEN_SETTINGS, action.action)
        assertNull(action.date)
    }

    @Test
    fun `all reviewed Beats lead back to today instead of reopening a completed day`() {
        val action = actionableInsightFor(today, 0, null, 3)

        assertEquals("Keep the review habit", action.title)
        assertEquals(InsightAction.OPEN_TODAY, action.action)
        assertNull(action.date)
    }

    private fun beat(date: LocalDate) = DayFeedItem(
        date = date,
        stays = emptyList(),
        route = listOf(RoutePoint(12.97, 77.59, isStay = false)),
        distanceMeters = 0.0,
        firstSeenMs = 1L,
        lastSeenMs = 2L,
        diaryPreview = null,
    )
}
