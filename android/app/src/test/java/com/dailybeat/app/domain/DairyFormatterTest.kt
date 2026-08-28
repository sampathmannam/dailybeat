package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Event
import org.junit.Assert.assertTrue
import org.junit.Test

class DairyFormatterTest {

    @Test
    fun formatEvents_includesTimeAndText() {
        val events = listOf(
            Event(timestamp = 1_704_000_000_000L, type = "manual", rawText = "Met IO at station"),
        )
        val result = DairyFormatter.formatEvents(events)
        assertTrue(result.contains("Met IO at station"))
        assertTrue(result.contains("hours"))
    }

    @Test
    fun formatEvents_emptyReturnsEmpty() {
        assertTrue(DairyFormatter.formatEvents(emptyList()).isEmpty())
    }
}
