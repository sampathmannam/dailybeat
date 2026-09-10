package com.dailybeat.app.cloud

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.domain.OutboundVisitFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * v3.8.1 shipped `Place.isPrivate` and `LocationVisit.hidden` as fields that were written, shown
 * and backed up but never read in a decision. A place marked "Private zone" was still sent to the
 * cloud provider, and a stop the officer hid during review was still sent with it. Nothing in the
 * suite caught that, because nothing asserted it.
 *
 * These are the assertions that would have.
 */
class PrivatePlaceExclusionTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val date: LocalDate = LocalDate.of(2026, 9, 10)

    private val safehouse = Place(
        id = 1,
        name = "Safehouse",
        latitude = 12.9700,
        longitude = 77.5900,
        radiusM = 150,
        isPrivate = true,
    )

    private val station = Place(
        id = 2,
        name = "Rasipuram Police Station",
        latitude = 11.4560,
        longitude = 78.1800,
        radiusM = 150,
        isPrivate = false,
    )

    private fun visitAt(
        lat: Double,
        lon: Double,
        label: String,
        hidden: Boolean = false,
    ) = LocationVisit(
        startMs = 1_757_500_000_000L,
        endMs = 1_757_503_600_000L,
        latitude = lat,
        longitude = lon,
        placeName = label,
        visitType = "dwell",
        hidden = hidden,
    )

    private fun build(visits: List<LocationVisit>, places: List<Place>, events: List<Event> = emptyList()) =
        DayContextBuilder.buildDetailed(
            date = date,
            officerName = "IPS Officer",
            visits = visits,
            events = events,
            places = places,
            zone = zone,
        )

    @Test
    fun `a stay inside a private zone never reaches the cloud payload`() {
        val built = build(
            visits = listOf(visitAt(12.9701, 77.5901, "Safehouse")),
            places = listOf(safehouse),
        )

        assertFalse("private place name leaked", built.text.contains("Safehouse"))
        assertEquals(0, built.visitRefCount)
    }

    @Test
    fun `a stay outside every private zone is still sent`() {
        val built = build(
            visits = listOf(visitAt(11.4561, 78.1801, "Rasipuram Police Station")),
            places = listOf(safehouse, station),
        )

        assertTrue(built.text.contains("Rasipuram Police Station"))
        assertEquals(1, built.visitRefCount)
    }

    @Test
    fun `marking a saved place private is what excludes it, not merely having saved it`() {
        val visits = listOf(visitAt(11.4561, 78.1801, "Rasipuram Police Station"))

        val asPublic = build(visits, listOf(station))
        val asPrivate = build(visits, listOf(station.copy(isPrivate = true)))

        assertEquals(1, asPublic.visitRefCount)
        assertEquals(0, asPrivate.visitRefCount)
    }

    @Test
    fun `a stop hidden during review never reaches the cloud payload`() {
        val built = build(
            visits = listOf(visitAt(11.4561, 78.1801, "Wrongly captured stop", hidden = true)),
            places = emptyList(),
        )

        assertFalse("hidden stop leaked", built.text.contains("Wrongly captured stop"))
        assertEquals(0, built.visitRefCount)
    }

    @Test
    fun `citation refs are renumbered over the surviving stays only`() {
        // If refs were numbered before filtering, [V2] would be sent while [V1] was withheld and
        // the integrity validator would reject the model's own correct citation.
        val built = build(
            visits = listOf(
                visitAt(12.9701, 77.5901, "Safehouse"),
                visitAt(11.4561, 78.1801, "Rasipuram Police Station"),
            ),
            places = listOf(safehouse, station),
        )

        assertTrue(built.text.contains("[V1]"))
        assertFalse(built.text.contains("[V2]"))
        assertEquals(1, built.visitRefCount)
    }

    @Test
    fun `no raw coordinates are ever written into the payload`() {
        val built = build(
            visits = listOf(visitAt(11.4561, 78.1801, "Rasipuram Police Station")),
            places = listOf(station),
        )

        // Four-decimal coordinates locate someone to about 11 metres. The model cites [V#] refs
        // and never needs the numbers.
        assertFalse(built.text.contains("11.4561"))
        assertFalse(built.text.contains("78.1801"))
        assertFalse(built.text.contains("11.456"))
        assertFalse(Regex("""\(-?\d+\.\d{4}, -?\d+\.\d{4}\)""").containsMatchIn(built.text))
    }

    @Test
    fun `events are unaffected by the visit filter`() {
        val built = build(
            visits = listOf(visitAt(12.9701, 77.5901, "Safehouse")),
            places = listOf(safehouse),
            events = listOf(Event(timestamp = 1_757_502_000_000L, type = "manual", rawText = "Briefing note.")),
        )

        assertTrue(built.text.contains("Briefing note."))
        assertEquals(1, built.eventRefCount)
    }

    @Test
    fun `the filter reports a private location independently of any visit`() {
        // VisitTracker uses this before reverse-geocoding, so a private zone is never sent to
        // Nominatim in the first place.
        assertTrue(OutboundVisitFilter.isPrivateLocation(12.9701, 77.5901, listOf(safehouse)))
        assertFalse(OutboundVisitFilter.isPrivateLocation(11.4561, 78.1801, listOf(safehouse, station)))
        assertFalse(OutboundVisitFilter.isPrivateLocation(12.9701, 77.5901, emptyList()))
    }

    @Test
    fun `a stay just outside a private radius is not swept up`() {
        // ~1.1 km north of the safehouse, well beyond its 150 m radius.
        val built = build(
            visits = listOf(visitAt(12.9800, 77.5900, "Market")),
            places = listOf(safehouse),
        )

        assertTrue(built.text.contains("Market"))
        assertEquals(1, built.visitRefCount)
    }
}
