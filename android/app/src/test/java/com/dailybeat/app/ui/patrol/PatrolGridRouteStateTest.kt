package com.dailybeat.app.ui.patrol

import com.dailybeat.app.patrolgrid.PatrolMapPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PatrolGridRouteStateTest {

    @Test
    fun `newer room evidence wins over a stale local refresh`() {
        val staleSnapshot = evidence(total = 1, pointCount = 1, latitudeBase = 12.0)
        val liveEvidence = evidence(total = 2, pointCount = 2, latitudeBase = 13.0)

        val selected = selectRouteDisplayEvidence(
            snapshot = staleSnapshot,
            observed = liveEvidence,
            observedIsNewer = true,
            protectFullerSnapshot = false,
        )

        assertEquals(liveEvidence, selected)
    }

    @Test
    fun `full server trail is not replaced by a smaller device list`() {
        val serverEvidence = evidence(total = 100, pointCount = 100, latitudeBase = 12.0)
        val deviceEvidence = evidence(total = 101, pointCount = 1, latitudeBase = 13.0)

        val selected = selectRouteDisplayEvidence(
            snapshot = serverEvidence,
            observed = deviceEvidence,
            observedIsNewer = true,
            protectFullerSnapshot = true,
        )

        assertEquals(serverEvidence, selected)
    }

    @Test
    fun `newer room trail wins when both candidates are equally complete`() {
        val serverEvidence = evidence(total = 2, pointCount = 2, latitudeBase = 12.0)
        val newerDeviceEvidence = evidence(total = 2, pointCount = 2, latitudeBase = 13.0)

        val selected = selectRouteDisplayEvidence(
            snapshot = serverEvidence,
            observed = newerDeviceEvidence,
            observedIsNewer = true,
            protectFullerSnapshot = true,
        )

        assertEquals(newerDeviceEvidence, selected)
    }

    @Test
    fun `selected live evidence carries unreadable point count with its route`() {
        val staleSnapshot = evidence(total = 1, pointCount = 1, latitudeBase = 12.0)
        val liveEvidence = evidence(
            total = 2,
            pointCount = 1,
            latitudeBase = 13.0,
            unreadable = 1,
        )

        val selected = selectRouteDisplayEvidence(
            snapshot = staleSnapshot,
            observed = liveEvidence,
            observedIsNewer = true,
            protectFullerSnapshot = false,
        )

        assertEquals(2, selected.recordedTrackPoints)
        assertEquals(1, selected.routePoints.size)
        assertEquals(1, selected.unreadableTrackPoints)
    }

    private fun evidence(
        total: Int,
        pointCount: Int,
        latitudeBase: Double,
        unreadable: Int = 0,
    ) = PatrolRouteDisplayEvidence(
        recordedTrackPoints = total,
        routePoints = List(pointCount) { index ->
            PatrolMapPoint(
                latitude = latitudeBase + index / 10_000.0,
                longitude = 77.0 + index / 10_000.0,
            )
        },
        unreadableTrackPoints = unreadable,
    )
}
