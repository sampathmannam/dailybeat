package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequentPlaceLearnerTest {

    @Test
    fun suggestClustersFrequentDwells() {
        val visits = listOf(
            dwell(1, 12.97, 77.59),
            dwell(2, 12.9705, 77.5905),
            dwell(3, 12.9702, 77.5902),
        )
        val suggestions = FrequentPlaceLearner.suggest(visits, emptyList())
        assertEquals(1, suggestions.size)
        assertTrue(suggestions.first().visitCount >= 3)
    }

    @Test
    fun suggestSkipsExistingPlaces() {
        val visits = listOf(
            dwell(1, 12.97, 77.59),
            dwell(2, 12.9705, 77.5905),
            dwell(3, 12.9702, 77.5902),
        )
        val existing = listOf(Place(name = "HQ", latitude = 12.97, longitude = 77.59, radiusM = 100))
        val suggestions = FrequentPlaceLearner.suggest(visits, existing)
        assertTrue(suggestions.isEmpty())
    }

    private fun dwell(id: Long, lat: Double, lon: Double) = LocationVisit(
        id = id,
        startMs = id * 1_000_000L,
        endMs = id * 1_000_000L + 600_000L,
        latitude = lat,
        longitude = lon,
        placeName = "HQ",
        visitType = "dwell",
    )
}
