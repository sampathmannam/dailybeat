package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.Event
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCardPlaceLabelTest {
    @Test fun generatedTitleDoesNotRepeatItsExactPlaceBelow() {
        assertTrue(event("visit", "Stay at Royal Oak").hasPlaceInGeneratedText())
        assertTrue(event("visit", "Travel · Royal Oak").hasPlaceInGeneratedText())
    }

    @Test fun personalProseAndDistinctMetadataRemainVisible() {
        assertFalse(event("manual", "Stay at Royal Oak").hasPlaceInGeneratedText())
        assertFalse(event("voice", "Travel · Royal Oak").hasPlaceInGeneratedText())
        assertFalse(event("visit", "Discussed delivery near Royal Oak").hasPlaceInGeneratedText())
        assertFalse(event("visit", "Travel recorded").hasPlaceInGeneratedText())
        assertFalse(event("visit", "Stay at Royal Oak").copy(placeName = null).hasPlaceInGeneratedText())
    }

    private fun event(type: String, text: String) = Event(
        timestamp = 1_000L, type = type, rawText = text, placeName = "Royal Oak",
    )
}
