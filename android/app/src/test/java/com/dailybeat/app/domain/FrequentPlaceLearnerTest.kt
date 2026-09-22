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

    @Test
    fun hiddenOrCorruptStopsNeverBecomeSuggestions() {
        val valid = listOf(dwell(1, 12.97, 77.59), dwell(2, 12.9701, 77.5901))
        val invalid = listOf(
            dwell(3, 12.97, 77.59).copy(hidden = true),
            dwell(3, 12.97, 77.59).copy(visitType = "transit"),
            dwell(3, 12.97, 77.59).copy(endMs = 3_000_000L),
            dwell(3, Double.NaN, 77.59),
            dwell(3, 0.0, 0.0),
            dwell(3, 12.97, 437.59),
        )
        invalid.forEach { stop ->
            assertTrue(FrequentPlaceLearner.suggest(valid + stop, emptyList()).isEmpty())
        }
    }

    @Test
    fun unreviewedStopsRemainSuggestionsThatRequireUserApproval() {
        val visits = (1L..3L).map { dwell(it, 12.97, 77.59).copy(reviewState = "needs_review") }

        assertEquals(1, FrequentPlaceLearner.suggest(visits, emptyList()).size)
    }

    @Test
    fun neighboringShopsWithDifferentNamesDoNotMergeIntoOneFictionalPlace() {
        val visits = listOf(
            dwell(1, 12.97, 77.59).copy(placeName = "Furniture shop"),
            dwell(2, 12.9701, 77.59).copy(placeName = "Restaurant"),
            dwell(3, 12.9702, 77.59).copy(placeName = "Bank"),
        )

        assertTrue(FrequentPlaceLearner.suggest(visits, emptyList()).isEmpty())
    }

    @Test
    fun chainOfStopsCannotBridgeTwoPlacesBeyondClusterLimit() {
        val visits = listOf(
            dwell(1, 12.97, 77.59),
            dwell(2, 12.9708, 77.59),
            dwell(3, 12.9692, 77.59),
        )

        assertTrue(FrequentPlaceLearner.suggest(visits, emptyList()).isEmpty())
    }

    @Test
    fun savedPrivateStopsCannotBecomeSuggestionsOutsideTheirZoneByAveraging() {
        val visits = listOf(
            dwell(1, 12.97, 77.59),
            dwell(2, 12.9705, 77.59),
            dwell(3, 12.9706, 77.59),
        )
        val privatePlace = Place(name = "Private", latitude = 12.97, longitude = 77.59,
            radiusM = 25, isPrivate = true)

        assertTrue(FrequentPlaceLearner.suggest(visits, listOf(privatePlace)).isEmpty())
    }

    @Test
    fun roadAddressDoesNotInventANameAndApproximateVenueKeepsItsQualifier() {
        val unnamed = (1L..3L).map {
            dwell(it, 12.97, 77.59).copy(placeName = "Unnamed place", address = "Trichy Road, Namakkal")
        }
        assertEquals("Frequent location", FrequentPlaceLearner.suggest(unnamed, emptyList()).single().name)
        val approximate = unnamed.map { it.copy(placeName = "Near Royal Oak") }
        assertEquals("Near Royal Oak", FrequentPlaceLearner.suggest(approximate, emptyList()).single().name)
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
