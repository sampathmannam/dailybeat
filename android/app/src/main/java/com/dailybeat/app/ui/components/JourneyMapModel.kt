package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.LocationVisit
import java.util.Locale
import kotlin.math.abs

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
    val centerLatitude: Double? = null,
    val centerLongitude: Double? = null,
    val latitudeSpan: Double = 0.0,
    val longitudeSpan: Double = 0.0,
    val crossesAntimeridian: Boolean = false,
) {
    val bounds: JourneyBounds
        get() = requireNotNull(boundsOrNull)

    val routeSegments: List<List<JourneyPoint>>
        get() {
            if (points.isEmpty()) return emptyList()
            val segments = mutableListOf(mutableListOf(points.first()))
            points.zipWithNext().forEach { (previous, next) ->
                if (abs(next.longitude - previous.longitude) > 180.0) {
                    segments += mutableListOf(next)
                } else {
                    segments.last() += next
                }
            }
            return segments
        }

    val cameraZoom: Int
        get() {
            if (points.size <= 1) return 16
            return when (maxOf(latitudeSpan, longitudeSpan)) {
                in 0.0..0.005 -> 16
                in 0.005..0.02 -> 15
                in 0.02..0.05 -> 14
                in 0.05..0.1 -> 13
                in 0.1..0.25 -> 12
                in 0.25..0.5 -> 11
                in 0.5..1.0 -> 10
                in 1.0..2.0 -> 9
                in 2.0..5.0 -> 8
                in 5.0..10.0 -> 7
                in 10.0..20.0 -> 6
                in 20.0..45.0 -> 5
                in 45.0..90.0 -> 4
                in 90.0..180.0 -> 3
                else -> 2
            }
        }

    val openStreetMapUrlOrNull: String?
        get() {
            val lat = centerLatitude ?: return null
            val lon = centerLongitude ?: return null
            return String.format(
                Locale.US,
                "https://www.openstreetmap.org/#map=%d/%.6f/%.6f",
                cameraZoom,
                lat,
                lon,
            )
        }

    companion object {
        const val WEB_MERCATOR_MAX_LATITUDE = 85.05112878
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
            val south = if (minLat == maxLat) {
                (minLat - SINGLE_POINT_PADDING).coerceAtLeast(-WEB_MERCATOR_MAX_LATITUDE)
            } else {
                minLat
            }
            val north = if (minLat == maxLat) {
                (maxLat + SINGLE_POINT_PADDING).coerceAtMost(WEB_MERCATOR_MAX_LATITUDE)
            } else {
                maxLat
            }
            val longitudeBounds = shortestLongitudeBounds(points.map { it.longitude })
            val bounds = JourneyBounds(
                south = south,
                west = longitudeBounds.west,
                north = north,
                east = longitudeBounds.east,
            )
            return JourneyMapModel(
                points = points,
                boundsOrNull = bounds,
                centerLatitude = (south + north) / 2,
                centerLongitude = longitudeBounds.center,
                latitudeSpan = north - south,
                longitudeSpan = longitudeBounds.span,
                crossesAntimeridian = longitudeBounds.crossesAntimeridian,
            )
        }

        private fun LocationVisit.hasMappableCoordinate(): Boolean =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -WEB_MERCATOR_MAX_LATITUDE..WEB_MERCATOR_MAX_LATITUDE &&
                longitude in -180.0..180.0 &&
                !(latitude == 0.0 && longitude == 0.0)

        private fun shortestLongitudeBounds(longitudes: List<Double>): LongitudeBounds {
            if (longitudes.size == 1) {
                return paddedLongitudeBounds(normalizeLongitude(longitudes.single()))
            }

            val sorted = longitudes.map(::toPositiveLongitude).sorted()
            var largestGapIndex = 0
            var largestGap = Double.NEGATIVE_INFINITY
            sorted.indices.forEach { index ->
                val current = sorted[index]
                val next = if (index == sorted.lastIndex) sorted.first() + 360.0 else sorted[index + 1]
                val gap = next - current
                if (gap > largestGap) {
                    largestGap = gap
                    largestGapIndex = index
                }
            }

            val startIndex = (largestGapIndex + 1) % sorted.size
            val start = sorted[startIndex]
            var end = sorted[largestGapIndex]
            if (end < start) end += 360.0
            val span = end - start
            if (span == 0.0) return paddedLongitudeBounds(normalizeLongitude(start))
            val west = normalizeLongitude(start)
            val east = normalizeLongitude(end)
            return LongitudeBounds(
                west = west,
                east = east,
                center = normalizeLongitude(start + span / 2),
                span = span,
                crossesAntimeridian = west > east,
            )
        }

        private fun toPositiveLongitude(longitude: Double): Double =
            ((longitude % 360.0) + 360.0) % 360.0

        private fun paddedLongitudeBounds(center: Double): LongitudeBounds {
            val west = (center - SINGLE_POINT_PADDING).coerceAtLeast(-180.0)
            val east = (center + SINGLE_POINT_PADDING).coerceAtMost(180.0)
            return if (west < east) {
                LongitudeBounds(west, east, center, east - west, false)
            } else {
                LongitudeBounds(-180.0, -180.0 + SINGLE_POINT_PADDING, -180.0, SINGLE_POINT_PADDING, false)
            }
        }

        private fun normalizeLongitude(longitude: Double): Double {
            val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
            return if (normalized == -180.0 && longitude > 0) 180.0 else normalized
        }
    }
}

private data class LongitudeBounds(
    val west: Double,
    val east: Double,
    val center: Double,
    val span: Double,
    val crossesAntimeridian: Boolean,
)
