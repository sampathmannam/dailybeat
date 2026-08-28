package com.dailybeat.app.cloud

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayContextBuilderTest {

    @Test
    fun buildIncludesVisitTimelineAndNotes() {
        val date = LocalDate.of(2026, 8, 28)
        val zone = ZoneId.of("UTC")
        val visit = LocationVisit(
            id = 1,
            startMs = 1_000_000L,
            endMs = 1_600_000L,
            latitude = 12.97,
            longitude = 77.59,
            placeName = "Police HQ",
            address = "MG Road, Bengaluru",
            visitType = "dwell",
        )
        val events = listOf(
            Event(
                id = 1,
                timestamp = 2_000_000L,
                type = "manual",
                rawText = "Briefed team on patrol plan.",
            ),
        )

        val context = DayContextBuilder.build(
            date = date,
            officerName = "IPS Officer",
            visits = listOf(visit),
            events = events,
            zone = zone,
        )

        assertTrue(context.contains("OFFICER: IPS Officer"))
        assertTrue(context.contains("LOCATION TIMELINE"))
        assertTrue(context.contains("Police HQ"))
        assertTrue(context.contains("OFFICER NOTES"))
        assertTrue(context.contains("Briefed team"))
    }

    @Test
    fun buildHandlesEmptyVisits() {
        val context = DayContextBuilder.build(
            date = LocalDate.of(2026, 1, 1),
            officerName = "Test",
            visits = emptyList(),
            events = emptyList(),
        )
        assertTrue(context.contains("No visit segments recorded yet"))
    }
}
