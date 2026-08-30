package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.LocationVisit
import java.util.Locale

data class JourneyPoint(
    val startMs: Long,
    val latitude: Double,
    val longitude: Double,
    val visitType: String,
)

data class JourneyBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

data class JourneyMapModel(
    val points: List<JourneyPoint>,
    val boundsOrNull: JourneyBounds?,
) {
    val bounds: JourneyBounds
        get() = requireNotNull(boundsOrNull)

    val openStreetMapUrlOrNull: String?
        get() {
            val currentBounds = boundsOrNull ?: return null
            val centerLat = (currentBounds.south + currentBounds.north) / 2
            val centerLon = (currentBounds.west + currentBounds.east) / 2
            val zoom = if (points.size == 1) 16 else 13
            return String.format(
                Locale.US,
                "https://www.openstreetmap.org/#map=%d/%.6f/%.6f",
                zoom,
                centerLat,
                centerLon,
            )
        }

    companion object {
        private const val SINGLE_POINT_PADDING = 0.001

        fun fromVisits(visits: List<LocationVisit>): JourneyMapModel {
            val points = visits
                .asSequence()
                .filter { it.hasMappableCoordinate() }
                .sortedBy { it.startMs }
                .map {
                    JourneyPoint(
                        startMs = it.startMs,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        visitType = it.visitType,
                    )
                }
                .toList()

            if (points.isEmpty()) return JourneyMapModel(points, null)

            val minLat = points.minOf { it.latitude }
            val maxLat = points.maxOf { it.latitude }
            val minLon = points.minOf { it.longitude }
            val maxLon = points.maxOf { it.longitude }
            val bounds = JourneyBounds(
                south = if (minLat == maxLat) minLat - SINGLE_POINT_PADDING else minLat,
                west = if (minLon == maxLon) minLon - SINGLE_POINT_PADDING else minLon,
                north = if (minLat == maxLat) maxLat + SINGLE_POINT_PADDING else maxLat,
                east = if (minLon == maxLon) maxLon + SINGLE_POINT_PADDING else maxLon,
            )
            return JourneyMapModel(points, bounds)
        }

        private fun LocationVisit.hasMappableCoordinate(): Boolean =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0)
    }
}
