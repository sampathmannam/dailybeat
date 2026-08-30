package com.dailybeat.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceInputValidatorTest {

    @Test
    fun acceptsCoordinateBoundaries() {
        assertNull(PlaceInputValidator.errorFor("North Pole", "90", "180"))
        assertNull(PlaceInputValidator.errorFor("South Pole", "-90", "-180"))
    }

    @Test
    fun rejectsCoordinatesOutsideEarthRanges() {
        assertEquals(
            "Latitude must be between -90 and 90.",
            PlaceInputValidator.errorFor("Impossible", "90.0001", "0"),
        )
        assertEquals(
            "Longitude must be between -180 and 180.",
            PlaceInputValidator.errorFor("Impossible", "0", "180.0001"),
        )
    }

    @Test
    fun rejectsMissingNameAndMalformedCoordinates() {
        assertEquals("Enter a place name.", PlaceInputValidator.errorFor(" ", "1", "1"))
        assertEquals(
            "Enter valid latitude and longitude.",
            PlaceInputValidator.errorFor("HQ", "north", "east"),
        )
    }
}
