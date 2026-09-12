package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.Event
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
}
