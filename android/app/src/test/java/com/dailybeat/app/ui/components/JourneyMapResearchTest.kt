package com.dailybeat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic regression fixtures, frozen before the first autoresearch experiment. */
class JourneyMapResearchTest {
    private fun point(time: Long, latitude: Double = 11.0, longitude: Double = 78.0) =
        JourneyPoint(time, latitude, longitude, "transit")

    @Test
    fun rejectedRouteFixDoesNotTurnMissingGeometryIntoASolidRoute() {
        for (badLatitude in listOf(Double.NaN, Double.POSITIVE_INFINITY, 91.0)) {
            val model = JourneyMapModel.fromPoints(listOf(
                point(100), point(200, latitude = badLatitude), point(300, longitude = 78.1),
            ))
            assertEquals(listOf(1, 1), model.routeSegments.map { it.size })
            assertEquals(listOf(100L, 300L), model.gapSegments.single().map { it.startMs })
        }
    }

    @Test
    fun rejectedFixPreservesAnExplicitCaptureGap() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(100), point(200, longitude = Double.NaN).copy(startsAfterGap = true),
            point(300, longitude = 78.1),
        ))
        assertTrue(model.points.last().startsAfterGap)
        assertEquals(listOf(1, 1), model.routeSegments.map { it.size })
    }

    @Test
    fun markerBetweenRejectedFixAndNextBreadcrumbDoesNotConsumeTheGap() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(100), point(200, latitude = Double.NaN),
            point(250).copy(drawsRoute = false, visitType = "stay"),
            point(300, longitude = 78.1),
        ))
        assertTrue(model.points.last().startsAfterGap)
        assertEquals(listOf(1, 1), model.routeSegments.map { it.size })
    }

    @Test
    fun rejectedStopMarkerDoesNotBreakValidBreadcrumbGeometry() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(100), point(200, latitude = Double.NaN).copy(drawsRoute = false),
            point(300, longitude = 78.1),
        ))
        assertEquals(listOf(2), model.routeSegments.map { it.size })
        assertTrue(model.gapSegments.isEmpty())
    }

    @Test
    fun invalidFixBeforeTheFirstBreadcrumbDoesNotFabricateAGap() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(50, latitude = Double.NaN), point(100), point(300, longitude = 78.1),
        ))
        assertFalse(model.points.first().startsAfterGap)
        assertEquals(listOf(2), model.routeSegments.map { it.size })
    }

    @Test
    fun gapSanitizationUsesChronologicalOrderEvenWhenInputIsUnsorted() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(300, longitude = 78.1), point(100), point(200, latitude = Double.NaN),
        ))
        assertEquals(listOf(100L, 300L), model.points.map { it.startMs })
        assertTrue(model.points.last().startsAfterGap)
    }

    @Test
    fun replayDoesNotInterpolateThroughARejectedFix() {
        val model = JourneyMapModel.fromPoints(listOf(
            point(100), point(200, latitude = Double.NaN), point(300, longitude = 78.1),
        ))
        assertEquals(listOf(100L), model.atPlaybackProgress(0.5f).points.map { it.startMs })
    }

    @Test
    fun positiveToNegativeDateLineAliasKeepsFiniteGeometryAndLatitudeMovement() {
        assertFiniteDateLineAlias(180.0, -180.0)
    }

    @Test
    fun negativeToPositiveDateLineAliasKeepsFiniteGeometryAndLatitudeMovement() {
        assertFiniteDateLineAlias(-180.0, 180.0)
    }

    @Test
    fun dateLineBoundaryGridAndReplayNeverEmitInvalidCoordinates() {
        val longitudes = listOf(-180.0, -179.99, 179.99, 180.0)
        for (from in longitudes) for (to in longitudes) {
            val model = JourneyMapModel.fromPoints(listOf(
                point(100, latitude = 10.0, longitude = from),
                point(200, latitude = 10.2, longitude = to),
            ))
            for (progress in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
                assertRenderable(model.atPlaybackProgress(progress))
            }
        }
    }

    private fun assertFiniteDateLineAlias(from: Double, to: Double) {
        val model = JourneyMapModel.fromPoints(listOf(
            point(100, latitude = 10.0, longitude = from),
            point(200, latitude = 10.2, longitude = to),
        ))
        assertRenderable(model)
        assertTrue(model.routeSegments.any { segment ->
            segment.zipWithNext().any { (a, b) -> a.latitude != b.latitude }
        })
        assertEquals(listOf(100L, 200L), model.points.map { it.startMs })
        assertTrue(model.longitudeSpan < 0.01)
    }

    private fun assertRenderable(model: JourneyMapModel) {
        for (segment in model.routeSegments) {
            for (point in segment) {
                assertTrue("Renderer must never receive NaN/infinity", point.latitude.isFinite())
                assertTrue(point.longitude.isFinite())
                assertTrue(point.longitude in -180.0..180.0)
                assertTrue(point.startMs in 100L..200L)
            }
            for ((a, b) in segment.zipWithNext()) {
                assertTrue("No world-spanning date-line seam", kotlin.math.abs(a.longitude - b.longitude) <= 180.0)
            }
        }
    }
}
