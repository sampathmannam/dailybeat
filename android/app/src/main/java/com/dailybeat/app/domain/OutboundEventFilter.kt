package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place

/** Conservative association: a note made during a withheld stop is withheld too.
 * Free text is not reliably classifiable; users must still inspect the outgoing preview.
 */
object OutboundEventFilter {
    fun forOutbound(events: List<Event>, visits: List<LocationVisit>, places: List<Place>): List<Event> {
        val allowed = OutboundVisitFilter.forOutbound(visits, places).toSet()
        val withheld = visits.filterNot { it in allowed }
        val labels = places.filter { it.isPrivate }.map { it.name } +
            withheld.flatMap { listOfNotNull(it.placeName, it.address) }
        return events.filter { event ->
            event.type != "visit" &&
                !(event.latitude != null && event.longitude != null &&
                    OutboundVisitFilter.isPrivateLocation(event.latitude, event.longitude, places)) &&
                withheld.none { event.timestamp in it.startMs..it.endMs } &&
                !mentionsRestrictedLabel(event.rawText + " " + event.placeName.orEmpty(), labels)
        }
    }

    fun mentionsRestrictedLabel(text: String, labels: List<String>): Boolean =
        labels.filter { it.isNotBlank() }.any { text.contains(it.trim(), ignoreCase = true) }
}
