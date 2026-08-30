package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.LocationVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyMapModelTest {

    @Test
    fun fromVisits_filtersInvalidCoordinatesAndOrdersChronologically() {
        val visits = listOf(
            visit(startMs = 300, latitude = 12.98, longitude = 77.61),
            visit(startMs = 100, latitude = 12.96, longitude = 77.59),
            visit(startMs = 200, latitude = Double.NaN, longitude = 77.60),
            visit(startMs = 250, latitude = 91.0, longitude = 77.60),
        )

        val model = JourneyMapModel.fromVisits(visits)

        assertEquals(listOf(100L, 300L), model.points.map { it.startMs })
        assertEquals(12.96, model.bounds.south, 0.0)
        assertEquals(12.98, model.bounds.north, 0.0)
        assertEquals(77.59, model.bounds.west, 0.0)
        assertEquals(77.61, model.bounds.east, 0.0)
    }

    @Test
    fun fromVisits_singlePointCreatesNonZeroCameraBounds() {
        val model = JourneyMapModel.fromVisits(
            listOf(visit(startMs = 100, latitude = 12.9716, longitude = 77.5946)),
        )

        assertEquals(0.002, model.bounds.north - model.bounds.south, 0.0000001)
        assertEquals(0.002, model.bounds.east - model.bounds.west, 0.0000001)
    }

    @Test
    fun fromVisits_allInvalidReturnsEmptyModel() {
        val model = JourneyMapModel.fromVisits(
            listOf(visit(startMs = 100, latitude = 0.0, longitude = 0.0)),
        )

        assertEquals(emptyList<JourneyPoint>(), model.points)
        assertNull(model.boundsOrNull)
        assertNull(model.openStreetMapUrlOrNull)
    }

    @Test
    fun openStreetMapUrl_centersJourneyWithoutLeakingOtherData() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 12.96, longitude = 77.58),
                visit(startMs = 200, latitude = 12.98, longitude = 77.62),
            ),
        )

        assertEquals(
            "https://www.openstreetmap.org/#map=13/12.970000/77.600000",
            model.openStreetMapUrlOrNull,
        )
    }

    private fun visit(
        startMs: Long,
        latitude: Double,
        longitude: Double,
    ) = LocationVisit(
        startMs = startMs,
        endMs = startMs + 60_000,
        latitude = latitude,
        longitude = longitude,
    )
}
