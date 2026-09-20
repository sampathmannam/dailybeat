package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.LocationVisit
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayPatternAnalysisTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 20)

    @Test
    fun `analysis compares today with visible local history`() {
        val visits = listOf(
            visit(today.minusDays(1), 9, "Police Station", "dwell"),
            visit(today.minusDays(1), 10, "Police Station", "dwell"),
            visit(today.minusDays(1), 11, null, "transit"),
            visit(today, 8, "Police Station", "dwell"),
            visit(today, 9, "Court", "dwell"),
            visit(today, 10, null, "transit"),
        )

        val result = buildTodayPatternAnalysis(
            today = today,
            zoneId = zone,
            todayVisits = visits.filter { it.startMs >= today.atStartOfDay(zone).toInstant().toEpochMilli() },
            recentVisits = visits,
        )

        assertTrue(result.hasUsefulHistory)
        assertEquals(2, result.observedDays)
        assertEquals(2, result.todayStops)
        assertEquals(2.0, result.averageStopsPerDay, 0.001)
        assertEquals("Police Station", result.recurringPlace)
        assertEquals(3, result.recurringPlaceVisits)
        assertEquals(MovementWindow.MORNING, result.commonMovementWindow)
        assertEquals(2, result.commonMovementWindowJourneys)
    }

    @Test
    fun `hidden and out of window visits never influence patterns`() {
        val visible = visit(today, 9, "Court", "dwell")
        val hidden = visit(today.minusDays(1), 10, "Sensitive place", "dwell", hidden = true)
        val old = visit(today.minusDays(40), 10, "Old place", "dwell")

        val result = buildTodayPatternAnalysis(
            today = today,
            zoneId = zone,
            todayVisits = listOf(visible),
            recentVisits = listOf(visible, hidden, old),
        )

        assertFalse(result.hasUsefulHistory)
        assertEquals(1, result.observedDays)
        assertEquals(null, result.recurringPlace)
        assertEquals(0, result.recurringPlaceVisits)
    }

    @Test
    fun `unknown labels are not presented as recurring places`() {
        val visits = listOf(
            visit(today.minusDays(1), 9, null, "dwell", address = "Unnamed place"),
            visit(today, 10, null, "dwell", address = "Unnamed place"),
        )

        val result = buildTodayPatternAnalysis(today, zone, visits.drop(1), visits)

        assertEquals(null, result.recurringPlace)
        assertEquals(0, result.recurringPlaceVisits)
    }

    private fun visit(
        date: LocalDate,
        hour: Int,
        place: String?,
        type: String,
        hidden: Boolean = false,
        address: String? = null,
    ) = LocationVisit(
        startMs = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        endMs = date.atTime(hour, 30).atZone(zone).toInstant().toEpochMilli(),
        latitude = 11.0,
        longitude = 78.0,
        placeName = place,
        address = address,
        visitType = type,
        hidden = hidden,
    )
}
