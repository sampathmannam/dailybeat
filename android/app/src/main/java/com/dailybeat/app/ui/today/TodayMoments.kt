package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import kotlin.math.abs

/**
 * The order Today lists the day's moments in: newest first.
 *
 * Today is the screen the officer just recorded something on, so the thing they added belongs at
 * the top where they are already looking. Diary keeps the opposite order on purpose — it reads as
 * the day's narrative, start to finish.
 *
 * Sorted rather than simply reversed: the DAO happens to return ascending timestamps today, but
 * this list's order is a product decision and should not silently invert if that query is ever
 * changed. Ties keep their relative order, so two moments saved in the same millisecond stay in
 * the order they were written.
 */
internal fun todayMomentsOrder(events: List<Event>): List<Event> =
    events.sortedByDescending { it.timestamp }

/**
 * Repairs the label on older transit events without rewriting the user's history.
 *
 * Early builds persisted the visit's resolved address in [LocationVisit], then copied the same
 * record into the moments stream as the bare word "Transit". The timestamp and coordinates were
 * retained, so Today can safely join the two local records and show the place immediately. New
 * capture records carry the label directly; this compatibility path keeps already-captured days
 * useful after an app update.
 */
internal fun todayMomentsForDisplay(
    events: List<Event>,
    visits: List<LocationVisit>,
): List<Event> = todayMomentsOrder(events.map { event ->
    if (!event.isLegacyTransitLabel()) return@map event

    val visit = visits.firstOrNull { candidate ->
        candidate.visitType.equals("transit", ignoreCase = true) &&
            candidate.startMs == event.timestamp &&
            coordinatesMatch(event, candidate)
    }
    val place = event.placeName.usablePlaceLabel()
        ?: visit?.placeName.usablePlaceLabel()
        ?: visit?.address.usablePlaceLabel()

    event.copy(
        rawText = "Travel recorded",
        placeName = place,
    )
})

private fun Event.isLegacyTransitLabel(): Boolean =
    type.equals("visit", ignoreCase = true) &&
        rawText.trim().lowercase() in setOf("transit", "recorded transit")

private fun coordinatesMatch(event: Event, visit: LocationVisit): Boolean {
    val latitude = event.latitude ?: return true
    val longitude = event.longitude ?: return true
    return abs(latitude - visit.latitude) < COORDINATE_MATCH_TOLERANCE &&
        abs(longitude - visit.longitude) < COORDINATE_MATCH_TOLERANCE
}

private fun String?.usablePlaceLabel(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !it.equals("Unnamed place", ignoreCase = true) }

private const val COORDINATE_MATCH_TOLERANCE = 0.000_01
