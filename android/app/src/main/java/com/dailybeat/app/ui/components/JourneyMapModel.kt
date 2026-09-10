package com.dailybeat.app.ui.components

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.feed.RoutePoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.sinh
import kotlin.math.tan

data class JourneyPoint(
    val startMs: Long,
    val latitude: Double,
    val longitude: Double,
    val visitType: String,
    val startsAfterGap: Boolean = false,
    val drawsRoute: Boolean = true,
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
    val stopPoints: List<JourneyPoint>
        get() = points.filter { it.visitType != "transit" }.ifEmpty {
            points.firstOrNull()?.let(::listOf).orEmpty()
        }

    val bounds: JourneyBounds
        get() = requireNotNull(boundsOrNull)

    val routeSegments: List<List<JourneyPoint>>
        get() {
            val routePoints = points.filter { it.drawsRoute }
            if (routePoints.isEmpty()) return emptyList()
            val segments = mutableListOf(mutableListOf(routePoints.first()))
            routePoints.zipWithNext().forEach { (previous, next) ->
                if (next.startsAfterGap) {
                    segments += mutableListOf(next)
                    return@forEach
                }
                val rawDelta = next.longitude - previous.longitude
                if (abs(rawDelta) > 180.0) {
                    val adjustedNextLongitude = if (rawDelta < 0) {
                        next.longitude + 360.0
                    } else {
                        next.longitude - 360.0
                    }
                    val boundaryLongitude = if (rawDelta < 0) 180.0 else -180.0
                    val oppositeBoundaryLongitude = -boundaryLongitude
                    val fraction = (boundaryLongitude - previous.longitude) /
                        (adjustedNextLongitude - previous.longitude)
                    val boundaryLatitude = previous.latitude +
                        (next.latitude - previous.latitude) * fraction
                    val boundaryStartMs = previous.startMs +
                        ((next.startMs - previous.startMs) * fraction).toLong()

                    segments.last() += previous.copy(
                        startMs = boundaryStartMs,
                        latitude = boundaryLatitude,
                        longitude = boundaryLongitude,
                    )
                    segments += mutableListOf(
                        next.copy(
                            startMs = boundaryStartMs,
                            latitude = boundaryLatitude,
                            longitude = oppositeBoundaryLongitude,
                        ),
                        next,
                    )
                } else {
                    segments.last() += next
                }
            }
            return segments
        }

    val gapSegments: List<List<JourneyPoint>>
        get() = points.filter { it.drawsRoute }.zipWithNext()
            .filter { (_, next) -> next.startsAfterGap }
            .map { (previous, next) -> listOf(previous, next) }

    val cameraZoom: Int
        get() = cameraZoomForViewport(
            widthPx = DEFAULT_MAP_WIDTH_PX,
            heightPx = DEFAULT_MAP_HEIGHT_PX,
            paddingPx = DEFAULT_MAP_PADDING_PX,
        )

    fun cameraZoomForViewport(widthPx: Int, heightPx: Int, paddingPx: Int): Int {
        if (points.size <= 1) return 16
        val longitudeFraction = (longitudeSpan / 360.0).coerceAtLeast(MIN_PROJECTED_SPAN)
        val latitudeFraction = abs(
            mercatorY(bounds.north) - mercatorY(bounds.south),
        ).coerceAtLeast(MIN_PROJECTED_SPAN)
        val contentWidth = (widthPx - paddingPx * 2).coerceAtLeast(1).toDouble()
        val contentHeight = (heightPx - paddingPx * 2).coerceAtLeast(1).toDouble()
        val horizontalZoom = log2(contentWidth / (TILE_SIZE_PX * longitudeFraction))
        val verticalZoom = log2(contentHeight / (TILE_SIZE_PX * latitudeFraction))
        return floor(min(horizontalZoom, verticalZoom)).toInt().coerceIn(1, 16)
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
        private const val TILE_SIZE_PX = 256.0
        private const val DEFAULT_MAP_WIDTH_PX = 360
        private const val DEFAULT_MAP_HEIGHT_PX = 220
        private const val DEFAULT_MAP_PADDING_PX = 48
        private const val MIN_PROJECTED_SPAN = 1e-9

        private fun mercatorY(latitude: Double): Double {
            val radians = Math.toRadians(latitude)
            return (1.0 - ln(tan(radians) + 1.0 / cos(radians)) / Math.PI) / 2.0
        }

        private fun latitudeFromMercatorY(y: Double): Double =
            Math.toDegrees(atan(sinh(Math.PI * (1.0 - 2.0 * y))))

        fun fromVisits(visits: List<LocationVisit>): JourneyMapModel = fromPoints(
            visits.map {
                JourneyPoint(
                    startMs = it.startMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    visitType = it.visitType,
                )
            },
        )

        fun fromRoute(route: List<RoutePoint>): JourneyMapModel = fromPoints(
            route.map {
                JourneyPoint(
                    startMs = it.timestampMs,
                    latitude = it.latitude,
                    longitude = it.longitude,
                    visitType = if (it.isStay) "stay" else "transit",
                    startsAfterGap = it.startsAfterGap,
                    drawsRoute = it.drawsRoute,
                )
            },
        )

        fun fromPoints(sourcePoints: List<JourneyPoint>): JourneyMapModel {
            val points = sourcePoints
                .asSequence()
                .filter { it.hasMappableCoordinate() }
                .sortedBy { it.startMs }
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
                centerLatitude = latitudeFromMercatorY((mercatorY(south) + mercatorY(north)) / 2),
                centerLongitude = longitudeBounds.center,
                latitudeSpan = north - south,
                longitudeSpan = longitudeBounds.span,
                crossesAntimeridian = longitudeBounds.crossesAntimeridian,
            )
        }

        private fun JourneyPoint.hasMappableCoordinate(): Boolean =
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

/**
 * Builds the route frame shown by the full-map replay. Progress follows the captured point order,
 * not wall-clock time, so a long stay does not make playback appear frozen. Missing capture
 * sections are skipped rather than inventing a route between their endpoints.
 */
internal fun JourneyMapModel.atPlaybackProgress(progress: Float): JourneyMapModel {
    val routePoints = points.filter { it.drawsRoute }
    if (routePoints.size < 2 || progress >= 1f) return this

    val clamped = progress.coerceIn(0f, 1f)
    val segmentPosition = clamped * routePoints.lastIndex
    val leftIndex = floor(segmentPosition).toInt().coerceAtMost(routePoints.lastIndex)
    val framePoints = routePoints.take(leftIndex + 1).toMutableList()
    val right = routePoints.getOrNull(leftIndex + 1)
    val fraction = segmentPosition - leftIndex
    if (right != null && fraction > 0f && !right.startsAfterGap) {
        framePoints += interpolateJourneyPoint(routePoints[leftIndex], right, fraction.toDouble())
    }

    val frameTime = framePoints.last().startMs
    framePoints += points.filter { !it.drawsRoute && it.startMs <= frameTime }
    return JourneyMapModel.fromPoints(framePoints)
}

internal fun journeyPlaybackDurationMillis(pointCount: Int): Int =
    (pointCount * 75).coerceIn(4_000, 18_000)

private fun interpolateJourneyPoint(
    from: JourneyPoint,
    to: JourneyPoint,
    fraction: Double,
): JourneyPoint {
    val rawLongitudeDelta = to.longitude - from.longitude
    val longitudeDelta = when {
        rawLongitudeDelta > 180.0 -> rawLongitudeDelta - 360.0
        rawLongitudeDelta < -180.0 -> rawLongitudeDelta + 360.0
        else -> rawLongitudeDelta
    }
    var longitude = from.longitude + longitudeDelta * fraction
    if (longitude > 180.0) longitude -= 360.0
    if (longitude < -180.0) longitude += 360.0
    return JourneyPoint(
        startMs = from.startMs + ((to.startMs - from.startMs) * fraction).toLong(),
        latitude = from.latitude + (to.latitude - from.latitude) * fraction,
        longitude = longitude,
        visitType = "transit",
    )
}

private data class LongitudeBounds(
    val west: Double,
    val east: Double,
    val center: Double,
    val span: Double,
    val crossesAntimeridian: Boolean,
)
