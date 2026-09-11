package com.dailybeat.app.ui.feed

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.model.BeatReview
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.domain.GeofenceMatcher
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** One stay on the day's route, e.g. "Rasipuram Police Station, 40 min". */
data class DayStay(
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationReliable: Boolean = true,
    val visitId: Long = 0,
) {
    val durationMinutes: Long get() = TimeUnit.MILLISECONDS.toMinutes(endMs - startMs).coerceAtLeast(0)
    val canBeNamed: Boolean get() = locationReliable && isUsableFeedCoordinate(latitude, longitude)
}

/** A point on the drawn route. Transit points carry the shape, stays carry the dots. */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val isStay: Boolean,
    val timestampMs: Long = 0,
    val startsAfterGap: Boolean = false,
    val drawsRoute: Boolean = true,
)

/** Everything one day's card in the feed shows. */
data class DayFeedItem(
    val date: LocalDate,
    val stays: List<DayStay>,
    val route: List<RoutePoint>,
    val distanceMeters: Double,
    val firstSeenMs: Long?,
    val lastSeenMs: Long?,
    val diaryPreview: String?,
    val title: String = "",
    val state: String = "needs_review",
    val captureGapCount: Int = 0,
    val distanceEstimated: Boolean = true,
) {
    val stayCount: Int get() = stays.size

    /** Total time between the first and last thing captured, i.e. how long the officer was out. */
    val activeMinutes: Long
        get() {
            val first = firstSeenMs ?: return 0
            val last = lastSeenMs ?: return 0
            return TimeUnit.MILLISECONDS.toMinutes(last - first).coerceAtLeast(0)
        }

    val trackedMinutes: Long get() = activeMinutes

    val distanceKm: Double get() = distanceMeters / 1000.0

    val hasRoute: Boolean get() = route.isNotEmpty()

    /** A day with nothing captured should not take up a card. */
    val isEmpty: Boolean get() = stays.isEmpty() && route.isEmpty() && diaryPreview.isNullOrBlank()
}

object DayFeedBuilder {

    private const val MAX_PREVIEW_CHARS = 220

    /**
     * [places] the officer has named are applied here rather than only at capture time, so
     * naming a spot relabels the stays already recorded there instead of appearing to do nothing.
     */
    fun build(
        date: LocalDate,
        visits: List<LocationVisit>,
        diaryText: String?,
        places: List<Place> = emptyList(),
        breadcrumbs: List<LocationBreadcrumb> = emptyList(),
        review: BeatReview? = null,
    ): DayFeedItem {
        val ordered = visits.filterNot { it.hidden }.sortedBy { it.startMs }
        val mappable = plausibleRoute(ordered.filter { it.hasUsableCoordinate() })
        val reliableCoordinateVisits = mappable.toSet()

        val stays = ordered
            .filter { it.visitType != "transit" }
            .map {
                DayStay(
                    name = it.displayName(places),
                    startMs = it.startMs,
                    endMs = maxOf(it.endMs, it.startMs),
                    latitude = it.latitude,
                    longitude = it.longitude,
                    locationReliable = it in reliableCoordinateVisits,
                    visitId = it.id,
                )
            }

        val orderedBreadcrumbs = plausibleBreadcrumbs(breadcrumbs.sortedBy { it.timestampMs })
        val gapStarts = orderedBreadcrumbs.zipWithNext()
            .filter { (previous, next) -> next.timestampMs - previous.timestampMs > CAPTURE_GAP_MS }
            .map { it.second.timestampMs }
            .toSet()
        val route = if (orderedBreadcrumbs.isNotEmpty()) {
            val stayPoints = mappable.filter { it.visitType != "transit" }.map {
                RoutePoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isStay = true,
                    timestampMs = it.startMs,
                    drawsRoute = false,
                )
            }
            (orderedBreadcrumbs.map {
                RoutePoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isStay = false,
                    timestampMs = it.timestampMs,
                    startsAfterGap = it.timestampMs in gapStarts,
                )
            } + stayPoints).sortedBy { it.timestampMs }
        } else {
            mappable.map {
                RoutePoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    isStay = it.visitType != "transit",
                    timestampMs = it.startMs,
                )
            }
        }

        val first = listOfNotNull(
            ordered.minOfOrNull { it.startMs },
            orderedBreadcrumbs.minOfOrNull { it.timestampMs },
        ).minOrNull()
        val last = listOfNotNull(
            ordered.maxOfOrNull { maxOf(it.endMs, it.startMs) },
            orderedBreadcrumbs.maxOfOrNull { it.timestampMs },
        ).maxOrNull()
        val generatedTitle = when {
            review?.title?.isNotBlank() == true -> review.title
            stays.size == 1 -> "A day around ${stays.first().name}"
            stays.size == 2 -> "${stays.first().name} and ${stays.last().name}"
            stays.size > 2 -> "Field rounds across ${stays.size} places"
            else -> "Your day in motion"
        }
        val inferredState = when {
            review?.state != null -> review.state
            date == com.dailybeat.app.util.DateKeys.today() -> "live"
            // Only a genuine reason — a capture gap, or a stay the officer flagged — asks for
            // review. An ordinary captured day is just "captured" and carries no badge, so Days
            // does not nag review on every past day. (The previous else also returned
            // "needs_review", which labelled every day.)
            gapStarts.isNotEmpty() || ordered.any { it.reviewState == "needs_review" } -> "needs_review"
            else -> "captured"
        }

        return DayFeedItem(
            date = date,
            stays = stays,
            route = route,
            distanceMeters = if (orderedBreadcrumbs.size >= 2) {
                breadcrumbDistanceMeters(orderedBreadcrumbs, gapStarts)
            } else {
                routeDistanceMeters(mappable)
            },
            firstSeenMs = first,
            lastSeenMs = last,
            diaryPreview = diaryText?.trim()?.takeIf { it.isNotEmpty() }?.let { preview ->
                if (preview.length <= MAX_PREVIEW_CHARS) preview else preview.take(MAX_PREVIEW_CHARS).trimEnd() + "…"
            },
            title = generatedTitle,
            state = inferredState,
            captureGapCount = gapStarts.size,
            distanceEstimated = orderedBreadcrumbs.size < 2 || orderedBreadcrumbs.any { it.quality != "good" },
        )
    }

    private fun plausibleBreadcrumbs(points: List<LocationBreadcrumb>): List<LocationBreadcrumb> {
        if (points.size < 2) return points.filter { it.hasUsableCoordinate() }
        val accepted = mutableListOf<LocationBreadcrumb>()
        points.filter { it.hasUsableCoordinate() }.forEach { candidate ->
            val previous = accepted.lastOrNull()
            if (previous == null || candidate.timestampMs - previous.timestampMs > CAPTURE_GAP_MS ||
                isPlausibleSegment(previous, candidate)
            ) accepted += candidate
        }
        return accepted
    }

    private fun LocationBreadcrumb.hasUsableCoordinate(): Boolean =
        isUsableFeedCoordinate(latitude, longitude)

    private fun isPlausibleSegment(from: LocationBreadcrumb, to: LocationBreadcrumb): Boolean {
        val seconds = ((to.timestampMs - from.timestampMs).coerceAtLeast(MIN_ROUTE_SEGMENT_MS)) / 1_000.0
        return distanceM(from.latitude, from.longitude, to.latitude, to.longitude) / seconds <=
            MAX_ROUTE_SPEED_METERS_PER_SECOND
    }

    private fun breadcrumbDistanceMeters(
        points: List<LocationBreadcrumb>,
        gapStarts: Set<Long>,
    ): Double = points.zipWithNext().sumOf { (from, to) ->
        if (to.timestampMs in gapStarts) 0.0 else distanceM(from.latitude, from.longitude, to.latitude, to.longitude)
    }.let { (it * 10).roundToLong() / 10.0 }

    private fun LocationVisit.displayName(places: List<Place>): String =
        GeofenceMatcher.matchPlace(latitude, longitude, places)?.name
            ?: placeName?.trim()?.takeIf { it.isNotEmpty() }
            ?: address?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unnamed place"

    private fun LocationVisit.hasUsableCoordinate(): Boolean =
        isUsableFeedCoordinate(latitude, longitude)

    /**
     * GPS occasionally returns a valid-looking coordinate thousands of kilometres away. Keep
     * such a point in the stop list for auditability, but do not let it flatten the route preview
     * or turn a local day's distance into a transcontinental journey.
     */
    private fun plausibleRoute(visits: List<LocationVisit>): List<LocationVisit> {
        if (visits.size < 2) return visits
        val firstPlausibleIndex = if (
            visits.size >= 3 &&
            !isPlausibleRouteSegment(visits[0], visits[1]) &&
            isPlausibleRouteSegment(visits[1], visits[2])
        ) {
            1
        } else {
            0
        }
        val accepted = mutableListOf(visits[firstPlausibleIndex])
        visits.drop(firstPlausibleIndex + 1).forEach { candidate ->
            if (isPlausibleRouteSegment(accepted.last(), candidate)) {
                accepted += candidate
            }
        }
        return accepted
    }

    private fun isPlausibleRouteSegment(from: LocationVisit, to: LocationVisit): Boolean {
        val distance = distanceM(from.latitude, from.longitude, to.latitude, to.longitude)
        val movementWindowMs = maxOf(
            to.startMs - from.endMs,
            to.endMs - to.startMs,
            MIN_ROUTE_SEGMENT_MS,
        )
        return distance / (movementWindowMs / 1000.0) <= MAX_ROUTE_SPEED_METERS_PER_SECOND
    }

    private fun routeDistanceMeters(visits: List<LocationVisit>): Double =
        visits.zipWithNext().sumOf { (from, to) ->
            distanceM(from.latitude, from.longitude, to.latitude, to.longitude)
        }.let { (it * 10).roundToLong() / 10.0 }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earth * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    private const val MIN_ROUTE_SEGMENT_MS = 60_000L
    private const val CAPTURE_GAP_MS = 10 * 60_000L
    private const val MAX_ROUTE_SPEED_METERS_PER_SECOND = 100.0 // 360 km/h
}

internal fun isUsableFeedCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)
