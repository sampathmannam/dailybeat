package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import kotlin.math.abs

/** Display-only repair shared by Today and Diary; saved history and personal prose stay intact. */
internal fun momentsForDisplay(
    events: List<Event>,
    visits: List<LocationVisit>,
    places: List<Place> = emptyList(),
): List<Event> {
    val visitsByStart = visits.groupBy { it.startMs }
    return events.mapNotNull { event ->
        val transit = event.generatedVisitIsTransit() ?: return@mapNotNull event
        val visit = visitsByStart[event.timestamp]?.filter { candidate ->
            candidate.visitType.equals("transit", ignoreCase = true) == transit &&
                coordinatesMatch(event, candidate)
        }?.singleOrNull()
        if (visit?.hidden == true) return@mapNotNull null
        val place = visit?.let { VisitLabels.name(it, places) }
            ?: VisitLabels.savedPlaceName(event.latitude, event.longitude, places)
            ?: VisitLabels.usable(event.placeName)
            ?: visit?.let { VisitLabels.approximateLocation(it.latitude, it.longitude) }
            ?: VisitLabels.approximateLocation(event.latitude, event.longitude)
            ?: VisitLabels.UNAVAILABLE
        event.copy(rawText = VisitLabels.momentText(transit, place), placeName = place)
    }
}

/** Only exact capture templates are rewritten; arbitrary visit prose is user-owned. */
private fun Event.generatedVisitIsTransit(): Boolean? {
    if (!type.equals("visit", ignoreCase = true)) return null
    val text = rawText.trim()
    if (listOf("Transit", "Recorded transit", "Travel recorded").any { text.equals(it, ignoreCase = true) }) return true
    if (text.equals("Stay recorded", ignoreCase = true) || text.equals("Stay at unnamed place", ignoreCase = true)) return false
    val capturedName = placeName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when (text) {
        "Stay at $capturedName" -> false
        "Stay · $capturedName" -> if (VisitLabels.isFallback(capturedName)) false else null
        "Travel · $capturedName" -> true
        else -> null
    }
}

private fun coordinatesMatch(event: Event, visit: LocationVisit): Boolean =
    (event.latitude == null || abs(event.latitude - visit.latitude) < 0.000_01) &&
        (event.longitude == null || abs(event.longitude - visit.longitude) < 0.000_01)
