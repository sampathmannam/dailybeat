package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceMatcherTest {

    @Test
    fun matchPlace_returnsPlaceWithinRadius() {
        val place = Place(name = "Office", latitude = 12.97, longitude = 77.59, radiusM = 200)
        val matched = GeofenceMatcher.matchPlace(12.971, 77.591, listOf(place))
        assertNotNull(matched)
        assertEquals("Office", matched?.name)
    }

    @Test
    fun matchPlace_returnsNullWhenOutsideRadius() {
        val place = Place(name = "Office", latitude = 12.97, longitude = 77.59, radiusM = 50)
        val matched = GeofenceMatcher.matchPlace(13.5, 78.0, listOf(place))
        assertNull(matched)
    }

    @Test
    fun distanceMeters_isZeroForSamePoint() {
        assertEquals(0.0, GeofenceMatcher.distanceMeters(1.0, 1.0, 1.0, 1.0), 0.01)
    }
}
