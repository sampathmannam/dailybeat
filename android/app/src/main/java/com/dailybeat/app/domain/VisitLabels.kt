package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import java.util.Locale

/** Local display labels only: no geocoding, inferred venue, or change to saved history. */
object VisitLabels {
    const val UNAVAILABLE = "Place name unavailable"

    private val placeholders = setOf(
        "unnamed place", "unknown place", "unknown location", "location unavailable",
        "place name unavailable", "transit", "recorded transit", "travel recorded", "en route",
    )

    /** Machine-generated fallback text must not hide an actual address. */
    fun usable(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.lowercase(Locale.ROOT) !in placeholders
    }

    fun momentText(transit: Boolean, name: String?): String = when {
        transit -> name?.let { "Travel · $it" } ?: "Travel recorded"
        else -> name?.let { "Stay at $it" } ?: "Stay recorded"
    }

    fun name(
        visit: LocationVisit,
        places: List<Place> = emptyList(),
        shortAddress: Boolean = false,
    ): String? {
        // A meaningful correction is more specific than a surrounding geofence. Hide/restore
        // also sets manuallyEdited, so that flag alone must not promote an old placeholder.
        if (visit.manuallyEdited) usable(visit.placeName)?.let { return it }
        savedPlaceName(visit.latitude, visit.longitude, places)?.let { return it }
        usable(visit.placeName)?.let { return it }
        val address = usable(visit.address) ?: return null
        return if (shortAddress) usable(address.substringBefore(",")) ?: address else address
    }

    fun savedPlaceName(latitude: Double?, longitude: Double?, places: List<Place>): String? {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            (latitude == 0.0 && longitude == 0.0)
        ) return null
        // Saved places are user-authored: even an unusual literal name must remain intact.
        return GeofenceMatcher.matchPlace(latitude, longitude, places)?.name
            ?.trim()?.takeIf { it.isNotEmpty() }
    }
}
