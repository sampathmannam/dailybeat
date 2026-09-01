package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.LocationVisit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JourneyMapModelTest {

    @Test
    fun previewFractions_evenlyDistributesOrderedPoints() {
        assertEquals(emptyList<Float>(), previewFractions(0))
        assertEquals(listOf(0.5f), previewFractions(1))
        assertEquals(listOf(0f, 0.5f, 1f), previewFractions(3))
    }

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
        assertEquals(77.59, model.bounds.west, 0.0000001)
        assertEquals(77.61, model.bounds.east, 0.0000001)
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
    fun fromVisits_multiplePointsOnSameLongitudeCreateNonZeroCameraBounds() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 12.97, longitude = 77.59),
                visit(startMs = 200, latitude = 12.98, longitude = 77.59),
            ),
        )

        assert(model.bounds.west < model.bounds.east)
        assert(model.longitudeSpan > 0.0)
    }

    @Test
    fun fromVisits_rejectsCoordinatesOutsideWebMercatorAndKeepsBoundsValid() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 90.0, longitude = 0.0),
                visit(startMs = 200, latitude = 85.05112878, longitude = 180.0),
            ),
        )

        assertEquals(1, model.points.size)
        assertEquals(85.05112878, model.points.single().latitude, 0.0)
        assert(model.bounds.north <= JourneyMapModel.WEB_MERCATOR_MAX_LATITUDE)
        assert(model.bounds.east <= 180.0)
        assert(model.bounds.south < model.bounds.north)
        assert(model.bounds.west < model.bounds.east)
    }

    @Test
    fun fromVisits_antimeridianJourneyUsesShortestCameraSpanAndSplitsRoute() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 10.0, longitude = 179.0),
                visit(startMs = 200, latitude = 10.1, longitude = -179.0),
            ),
        )

        assertEquals(true, model.crossesAntimeridian)
        assertEquals(2.0, model.longitudeSpan, 0.0000001)
        assertEquals(180.0, kotlin.math.abs(requireNotNull(model.centerLongitude)), 0.0000001)
        assertEquals(listOf(2, 2), model.routeSegments.map { it.size })
        assertEquals(180.0, model.routeSegments[0].last().longitude, 0.0)
        assertEquals(-180.0, model.routeSegments[1].first().longitude, 0.0)
        assertEquals(10.05, model.routeSegments[0].last().latitude, 0.0000001)
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

    @Test
    fun openStreetMapUrl_zoomsOutForWideJourney() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 10.0, longitude = 1.0),
                visit(startMs = 200, latitude = 10.0, longitude = 81.0),
            ),
        )

        assertEquals(
            "https://www.openstreetmap.org/#map=2/10.000000/41.000000",
            model.openStreetMapUrlOrNull,
        )
    }

    @Test
    fun cameraZoom_accountsForMercatorLatitudeAndPhoneViewport() {
        val equator = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 0.1, longitude = 10.0),
                visit(startMs = 200, latitude = 10.1, longitude = 10.0),
            ),
        )
        val highLatitude = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 70.0, longitude = 10.0),
                visit(startMs = 200, latitude = 80.0, longitude = 10.0),
            ),
        )

        assert(highLatitude.cameraZoom < equator.cameraZoom)
    }

    @Test
    fun cameraZoom_usesMeasuredViewportAndPadding() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 10.0, longitude = 1.0),
                visit(startMs = 200, latitude = 10.0, longitude = 81.0),
            ),
        )

        val phoneZoom = model.cameraZoomForViewport(widthPx = 1080, heightPx = 660, paddingPx = 144)
        val narrowZoom = model.cameraZoomForViewport(widthPx = 320, heightPx = 220, paddingPx = 48)

        assert(phoneZoom > narrowZoom)
        assertEquals(1, narrowZoom)
    }

    @Test
    fun fromVisits_usesMercatorMidpointAtHighLatitude() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 70.0, longitude = 10.0),
                visit(startMs = 200, latitude = 80.0, longitude = 10.0),
            ),
        )

        assert(requireNotNull(model.centerLatitude) > 75.0)
    }

    @Test
    fun fromVisits_splitsReverseAntimeridianCrossingIntoRenderableSegments() {
        val model = JourneyMapModel.fromVisits(
            listOf(
                visit(startMs = 100, latitude = 10.0, longitude = -179.0),
                visit(startMs = 200, latitude = 10.2, longitude = 179.0),
            ),
        )

        assertEquals(listOf(2, 2), model.routeSegments.map { it.size })
        assertEquals(-180.0, model.routeSegments[0].last().longitude, 0.0)
        assertEquals(180.0, model.routeSegments[1].first().longitude, 0.0)
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
