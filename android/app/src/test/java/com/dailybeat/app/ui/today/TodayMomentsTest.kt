package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today never listed the day's moments at all: TodayViewModel collected `todayEvents` and the
 * screen never read it, so a moment saved from Today's own sheet was visible only in Diary, one
 * navigation hop away. These tests pin the order that list is shown in.
 */
class TodayMomentsTest {

    private fun event(id: Long, at: Long, text: String = "note") =
        Event(id = id, timestamp = at, type = "manual", rawText = text)

    @Test
    fun `moments are listed newest first`() {
        // The DAO returns ascending timestamps; Today shows the reverse.
        val ordered = todayMomentsOrder(
            listOf(
                event(1, 8 * 3_600_000, "Headquarters"),
                event(2, 9 * 3_600_000, "Morning briefing"),
                event(3, 11 * 3_600_000, "District court"),
            ),
        )

        assertEquals(listOf(3L, 2L, 1L), ordered.map { it.id })
    }

    @Test
    fun `order does not depend on the order the query returned`() {
        // The sort is a product decision, not an inherited side effect of the DAO's ORDER BY.
        // Shuffled input must produce the same list as sorted input.
        val events = listOf(
            event(2, 9 * 3_600_000),
            event(3, 11 * 3_600_000),
            event(1, 8 * 3_600_000),
        )

        assertEquals(listOf(3L, 2L, 1L), todayMomentsOrder(events).map { it.id })
    }

    @Test
    fun `moments saved in the same millisecond keep the order they were written`() {
        val sameInstant = 10 * 3_600_000L
        val ordered = todayMomentsOrder(
            listOf(event(1, sameInstant, "first"), event(2, sameInstant, "second")),
        )

        assertEquals(listOf("first", "second"), ordered.map { it.rawText })
    }

    @Test
    fun `an empty day produces an empty list rather than throwing`() {
        assertTrue(todayMomentsOrder(emptyList()).isEmpty())
    }

    @Test
    fun `nothing is dropped`() {
        val events = (1L..20L).map { event(it, it * 60_000) }

        assertEquals(20, todayMomentsOrder(events).size)
        assertEquals(events.map { it.id }.toSet(), todayMomentsOrder(events).map { it.id }.toSet())
    }

    @Test
    fun `legacy transit moment uses the matching visit address`() {
        val event = Event(
            id = 7,
            timestamp = 9_000,
            type = "visit",
            rawText = "Transit",
            latitude = 11.4557,
            longitude = 78.1856,
        )
        val visit = LocationVisit(
            id = 12,
            startMs = 9_000,
            endMs = 12_000,
            latitude = 11.4557,
            longitude = 78.1856,
            address = "Paramathi Road, Namakkal",
            visitType = "transit",
        )

        val displayed = todayMomentsForDisplay(listOf(event), listOf(visit)).single()

        assertEquals("Travel · Paramathi Road, Namakkal", displayed.rawText)
        assertEquals("Paramathi Road, Namakkal", displayed.placeName)
    }

    @Test
    fun `legacy transit moment never borrows a place from another coordinate`() {
        val event = Event(
            id = 7,
            timestamp = 9_000,
            type = "visit",
            rawText = "Transit",
            latitude = 11.4557,
            longitude = 78.1856,
        )
        val unrelated = LocationVisit(
            id = 12,
            startMs = 9_000,
            endMs = 12_000,
            latitude = 12.4557,
            longitude = 79.1856,
            address = "Wrong road",
            visitType = "transit",
        )

        val displayed = todayMomentsForDisplay(listOf(event), listOf(unrelated)).single()

        assertEquals("Travel recorded", displayed.rawText)
        assertEquals(null, displayed.placeName)
    }

    @Test
    fun `current generated travel joins a later resolved visit label`() {
        val captured = capturedMoment("Travel recorded")
        val displayed = todayMomentsForDisplay(listOf(captured), listOf(recordedVisit("transit"))).single()

        assertEquals("Travel · Namakkal", displayed.rawText)
        assertEquals("Namakkal", displayed.placeName)
        assertEquals("Travel recorded", captured.rawText)
        assertEquals(null, captured.placeName)
    }

    @Test
    fun `generated stay placeholder uses its current visit address`() {
        val captured = capturedMoment("Stay at unnamed place", placeName = "Unnamed place")
        val visit = recordedVisit("dwell").copy(placeName = "Unnamed place", address = "Paramathi Road, Namakkal")
        val displayed = todayMomentsForDisplay(listOf(captured), listOf(visit)).single()

        assertEquals("Stay at Paramathi Road, Namakkal", displayed.rawText)
        assertEquals("Paramathi Road, Namakkal", displayed.placeName)
        assertEquals("Unnamed place", visit.placeName)
    }

    @Test
    fun `all generated formats follow an explicit visit rename`() {
        listOf("Stay at Old name" to "dwell", "Travel · Old name" to "transit").forEach { (text, type) ->
            val captured = capturedMoment(text, "Old name")
            val visit = recordedVisit(type).copy(placeName = "Royal Oak", manuallyEdited = true)
            val displayed = todayMomentsForDisplay(listOf(captured), listOf(visit), listOf(savedPlace())).single()

            assertEquals("Royal Oak", displayed.placeName)
            assertEquals(if (type == "transit") "Travel · Royal Oak" else "Stay at Royal Oak", displayed.rawText)
        }
    }

    @Test
    fun `saved place names repair previously unnamed generated moments`() {
        val displayed = todayMomentsForDisplay(
            listOf(capturedMoment("Stay recorded")),
            listOf(recordedVisit("dwell").copy(placeName = null, address = "Unnamed place")),
            listOf(savedPlace()),
        ).single()

        assertEquals("Stay at Camp office", displayed.rawText)
        assertEquals("Camp office", displayed.placeName)
    }

    @Test
    fun `manual voice and custom visit prose remain byte for byte unchanged`() {
        val events = listOf(
            capturedMoment("Stay at Old name", "Old name").copy(type = "manual"),
            capturedMoment("Travel recorded").copy(type = "voice"),
            capturedMoment("Stay at Old name until the meeting ends", "Old name"),
        )

        assertEquals(events, todayMomentsForDisplay(events, listOf(recordedVisit("dwell")), listOf(savedPlace())))
    }

    @Test
    fun `a legacy moment without coordinates does not choose between colliding visits`() {
        val captured = capturedMoment("Transit").copy(latitude = null, longitude = null)
        val first = recordedVisit("transit")
        val displayed = todayMomentsForDisplay(
            listOf(captured), listOf(first, first.copy(latitude = 12.0, placeName = "Unrelated place")),
        ).single()

        assertEquals("Travel recorded", displayed.rawText)
        assertEquals(null, displayed.placeName)
    }

    @Test
    fun `differently typed visits cannot relabel a moment`() {
        val displayed = todayMomentsForDisplay(
            listOf(capturedMoment("Travel recorded")),
            listOf(recordedVisit("dwell")),
        ).single()

        assertEquals("Travel recorded", displayed.rawText)
        assertEquals(null, displayed.placeName)
    }

    @Test
    fun `hidden visit suppresses only its unambiguous generated copy`() {
        val generated = capturedMoment("Stay at Namakkal", "Namakkal")
        val manual = generated.copy(id = 8, type = "manual")
        val custom = generated.copy(id = 9, rawText = "Met a colleague in Namakkal")
        val unrelated = generated.copy(id = 10, latitude = 12.0)
        val displayed = todayMomentsForDisplay(
            listOf(generated, manual, custom, unrelated),
            listOf(recordedVisit("dwell").copy(hidden = true)),
        )

        assertEquals(listOf(manual, custom, unrelated), displayed)
    }

    @Test
    fun `ambiguous hidden visit cannot suppress or label another moment`() {
        val generated = capturedMoment("Stay recorded").copy(latitude = null, longitude = null)
        val visit = recordedVisit("dwell")
        val displayed = todayMomentsForDisplay(
            listOf(generated), listOf(visit.copy(hidden = true), visit.copy(id = 13, latitude = 12.0)),
        ).single()

        assertEquals(generated, displayed)
    }

    @Test
    fun `unknown stays remain truthful without a made up venue`() {
        val displayed = todayMomentsForDisplay(
            listOf(capturedMoment("Stay at unnamed place")),
            listOf(recordedVisit("dwell").copy(placeName = null, address = "Unnamed place")),
        ).single()

        assertEquals("Stay recorded", displayed.rawText)
        assertEquals(null, displayed.placeName)
    }

    private fun capturedMoment(text: String, placeName: String? = null) = Event(
        id = 7, timestamp = 9_000, type = "visit", rawText = text, placeName = placeName,
        latitude = 11.4557, longitude = 78.1856,
    )

    private fun recordedVisit(type: String) = LocationVisit(
        id = 12, startMs = 9_000, endMs = 12_000, latitude = 11.4557, longitude = 78.1856,
        placeName = "Namakkal", visitType = type,
    )

    private fun savedPlace() = Place(name = "Camp office", latitude = 11.4557, longitude = 78.1856)
}
