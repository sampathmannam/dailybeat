package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place

/**
 * Decides which stays may leave the device.
 *
 * Two controls the officer sets by hand feed into this and nothing else:
 *
 * - **Private zone** (`Place.isPrivate`) — a saved place they marked private. A home address, an
 *   informant's meeting point or a safehouse is exactly what gets this flag, so a stay that falls
 *   inside one must never reach a cloud provider or a shared export.
 * - **Hidden stop** (`LocationVisit.hidden`) — a stay they removed from the day during review.
 *   Removing it from their own screen while still transmitting it would make the UI teach a false
 *   belief.
 *
 * Both are applied here, at one choke point, rather than at each call site. Anything that builds a
 * payload leaving the device goes through [forOutbound]; local screens keep showing the full record
 * because the data never left the phone.
 */
object OutboundVisitFilter {

    /** The stays that may be sent to a cloud provider or written into a shared export. */
    fun forOutbound(visits: List<LocationVisit>, places: List<Place>): List<LocationVisit> {
        val privateZones = places.filter { it.isPrivate }
        return visits.filterNot { visit ->
            visit.hidden || isInsidePrivateZone(visit.latitude, visit.longitude, privateZones)
        }
    }

    /**
     * Whether a coordinate falls inside a place the officer marked private.
     *
     * Callers pass the full place list; filtering to private zones happens here so a caller cannot
     * get it subtly wrong.
     */
    fun isPrivateLocation(latitude: Double, longitude: Double, places: List<Place>): Boolean =
        isInsidePrivateZone(latitude, longitude, places.filter { it.isPrivate })

    private fun isInsidePrivateZone(
        latitude: Double,
        longitude: Double,
        privateZones: List<Place>,
    ): Boolean = GeofenceMatcher.matchPlace(latitude, longitude, privateZones) != null
}
