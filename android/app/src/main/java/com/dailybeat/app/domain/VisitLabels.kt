package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import java.util.Locale
import kotlin.math.roundToInt

/** Local display labels only: no network geocoding, inferred venue, or change to saved history. */
object VisitLabels {
    const val UNAVAILABLE = "No GPS fix recorded"
    private const val APPROXIMATE_PREFIX = "Approx. location · "
    private const val AREA_PREFIX = "Approx. area · "
    // Older captures stored raw coordinate pairs or just the latitude as their place name.
    private val legacyCoordinate = Regex("(?i)(?:location\\s*)?[-+]?\\d{1,3}\\.\\d{3,}(?:\\s*[,/]\\s*[-+]?\\d{1,3}\\.\\d{3,})?")

    private val placeholders = setOf(
        "unnamed place", "unknown place", "unknown location", "location unavailable",
        "place name unavailable", "transit", "recorded transit", "travel recorded", "en route",
        "no gps fix recorded",
    )

    /** Machine-generated fallback text must not hide an actual address. */
    fun usable(value: String?): String? = value?.trim()?.takeIf {
        it.isNotEmpty() && it.lowercase(Locale.ROOT) !in placeholders && !isApproximate(it)
    }

    fun isApproximate(label: String?): Boolean = label?.let {
        it.startsWith(APPROXIMATE_PREFIX) || it.startsWith(AREA_PREFIX) || legacyCoordinate.matches(it.trim())
    } == true
    fun isFallback(label: String?): Boolean = label == null || label == UNAVAILABLE || isApproximate(label)

    /** Named town reference, not a business, street or assertion of municipal boundaries. */
    fun approximateLocation(latitude: Double?, longitude: Double?): String? {
        if (!validCoordinates(latitude, longitude)) return null
        val town = OfflineTownIndex.bundled?.nearest(latitude!!, longitude!!)
            ?: return AREA_PREFIX + "Add a place name"
        return AREA_PREFIX + if (town.distanceKm <= 10.0) "Near ${town.name}"
            else "About ${((town.distanceKm / 5).roundToInt() * 5).coerceAtLeast(10)} km from ${town.name}"
    }

    fun displayName(visit: LocationVisit, places: List<Place> = emptyList(), shortAddress: Boolean = false): String =
        name(visit, places, shortAddress)
            ?: approximateLocation(visit.latitude, visit.longitude)
            ?: UNAVAILABLE

    fun momentText(transit: Boolean, name: String?): String = when {
        transit -> name?.let { "Travel · $it" } ?: "Travel recorded"
        name != null && isFallback(name) -> "Stay · $name"
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
        if (!validCoordinates(latitude, longitude)) return null
        // Saved places are user-authored: even an unusual literal name must remain intact.
        return GeofenceMatcher.matchPlace(latitude!!, longitude!!, places)?.name
            ?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun validCoordinates(latitude: Double?, longitude: Double?): Boolean =
        latitude != null && longitude != null && latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            (latitude != 0.0 || longitude != 0.0)
}
