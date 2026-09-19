package com.dailybeat.app.cloud

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayContextBuilderTest {

    @Test
    fun buildIncludesCitationRefs() {
        val visit = LocationVisit(
            startMs = 1_000_000L,
            endMs = 1_600_000L,
            latitude = 12.97,
            longitude = 77.59,
            placeName = "Police HQ",
            visitType = "dwell",
        )
        val event = Event(timestamp = 2_000_000L, type = "manual", rawText = "Briefing note.")
        val built = DayContextBuilder.buildDetailed(
            date = LocalDate.of(2026, 8, 28),
            officerName = "IPS Officer",
            visits = listOf(visit),
            events = listOf(event),
            places = emptyList(),
            zone = ZoneId.of("UTC"),
        )
        assertTrue(built.text.contains("[V1]"))
        assertTrue(built.text.contains("[E1]"))
        assertTrue(built.text.contains("CITATION RULE"))
    }
    @org.junit.Test
    fun `legacy geocoder coordinate labels never enter a cloud prompt`() {
        val context = DayContextBuilder.build(java.time.LocalDate.of(2026,9,19), "",
            listOf(com.dailybeat.app.data.model.LocationVisit(startMs=1000,endMs=601000,
                latitude=11.4557,longitude=78.1856,placeName="Location 11.4557",address="Location 11.4557, 78.1856")),
            emptyList(),emptyList())
        org.junit.Assert.assertFalse(context.contains("11.4557"))
        org.junit.Assert.assertFalse(context.contains("78.1856"))
        org.junit.Assert.assertTrue(context.contains("Unnamed place"))
    }

}
