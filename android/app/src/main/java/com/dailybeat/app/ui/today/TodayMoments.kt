package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.domain.VisitLabels
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
 * Joins generated moments to current local visit/place labels without rewriting saved history.
 *
 * Early builds persisted the visit's resolved address in [LocationVisit], then copied the same
 * record into the moments stream as the bare word "Transit". The timestamp and coordinates were
 * retained, so Today can safely join the two local records and show the place immediately. The
 * same join is needed for current travel and stay events when a visit is named after capture.
 * User-authored notes and non-template prose are never rewritten.
 */
internal fun todayMomentsForDisplay(
    events: List<Event>,
    visits: List<LocationVisit>,
    places: List<Place> = emptyList(),
): List<Event> {
    val visitsByStart = visits.groupBy { it.startMs }
    return todayMomentsOrder(events.mapNotNull { event ->
        val transit = event.generatedVisitIsTransit() ?: return@mapNotNull event

        // Missing coordinates in legacy events are acceptable only for an unambiguous match.
        val visit = visitsByStart[event.timestamp]?.filter { candidate ->
            candidate.visitType.equals("transit", ignoreCase = true) == transit &&
                coordinatesMatch(event, candidate)
        }?.singleOrNull()
        // Hiding a stop also hides its generated copy; manual notes remain independent.
        if (visit?.hidden == true) return@mapNotNull null
        val place = visit?.let { VisitLabels.name(it, places) }
            ?: VisitLabels.savedPlaceName(event.latitude, event.longitude, places)
            ?: VisitLabels.usable(event.placeName)

        event.copy(
            rawText = VisitLabels.momentText(transit, place),
            placeName = place,
        )
    })
}

/** Recognize only exact capture templates, not arbitrary prose that happens to mention a stay. */
private fun Event.generatedVisitIsTransit(): Boolean? {
    if (!type.equals("visit", ignoreCase = true)) return null
    val text = rawText.trim()
    if (listOf("Transit", "Recorded transit", "Travel recorded").any { text.equals(it, ignoreCase = true) }) {
        return true
    }
    if (text.equals("Stay recorded", ignoreCase = true) || text.equals("Stay at unnamed place", ignoreCase = true)) {
        return false
    }
    val capturedName = placeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (text) {
        "Stay at $capturedName" -> false
        "Travel · $capturedName" -> true
        else -> null
    }
}

private fun coordinatesMatch(event: Event, visit: LocationVisit): Boolean {
    return (event.latitude == null || abs(event.latitude - visit.latitude) < COORDINATE_MATCH_TOLERANCE) &&
        (event.longitude == null || abs(event.longitude - visit.longitude) < COORDINATE_MATCH_TOLERANCE)
}

private const val COORDINATE_MATCH_TOLERANCE = 0.000_01
