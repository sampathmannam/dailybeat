package com.dailybeat.app.ui.feed

import com.dailybeat.app.data.model.LocationVisit
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
) {
    val durationMinutes: Long get() = TimeUnit.MILLISECONDS.toMinutes(endMs - startMs).coerceAtLeast(0)
}

/** A point on the drawn route. Transit points carry the shape, stays carry the dots. */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val isStay: Boolean,
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
) {
    val stayCount: Int get() = stays.size

    /** Total time between the first and last thing captured, i.e. how long the officer was out. */
    val activeMinutes: Long
        get() {
            val first = firstSeenMs ?: return 0
            val last = lastSeenMs ?: return 0
            return TimeUnit.MILLISECONDS.toMinutes(last - first).coerceAtLeast(0)
        }

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
    ): DayFeedItem {
        val ordered = visits.sortedBy { it.startMs }
        val mappable = ordered.filter { it.hasUsableCoordinate() }

        val stays = ordered
            .filter { it.visitType != "transit" }
            .map {
                DayStay(
                    name = it.displayName(places),
                    startMs = it.startMs,
                    endMs = maxOf(it.endMs, it.startMs),
                    latitude = it.latitude,
                    longitude = it.longitude,
                )
            }

        val route = mappable.map {
            RoutePoint(
                latitude = it.latitude,
                longitude = it.longitude,
                isStay = it.visitType != "transit",
            )
        }

        return DayFeedItem(
            date = date,
            stays = stays,
            route = route,
            distanceMeters = routeDistanceMeters(mappable),
            firstSeenMs = ordered.minOfOrNull { it.startMs },
            lastSeenMs = ordered.maxOfOrNull { maxOf(it.endMs, it.startMs) },
            diaryPreview = diaryText?.trim()?.takeIf { it.isNotEmpty() }?.let { preview ->
                if (preview.length <= MAX_PREVIEW_CHARS) preview else preview.take(MAX_PREVIEW_CHARS).trimEnd() + "…"
            },
        )
    }

    private fun LocationVisit.displayName(places: List<Place>): String =
        GeofenceMatcher.matchPlace(latitude, longitude, places)?.name
            ?: placeName?.trim()?.takeIf { it.isNotEmpty() }
            ?: address?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Unnamed place"

    private fun LocationVisit.hasUsableCoordinate(): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)

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
}
