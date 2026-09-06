package com.dailybeat.app.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FeedPresentationTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 6)

    @Test
    fun `distance reads in metres below a kilometre and kilometres above`() {
        assertEquals("450 m", formatDistance(0.45))
        assertEquals("1.0 km", formatDistance(1.0))
        assertEquals("12.4 km", formatDistance(12.42))
    }

    @Test
    fun `a day that went nowhere shows a dash rather than zero`() {
        assertEquals("—", formatDistance(0.0))
        assertEquals("—", formatDuration(0))
    }

    @Test
    fun `duration reads in hours and minutes`() {
        assertEquals("40 min", formatDuration(40))
        assertEquals("2 h", formatDuration(120))
        assertEquals("2 h 5 min", formatDuration(125))
    }

    @Test
    fun `recent days are named rather than dated`() {
        assertEquals("Today", relativeDayLabel(today, today))
        assertEquals("Yesterday", relativeDayLabel(today.minusDays(1), today))
        assertEquals("4 days ago", relativeDayLabel(today.minusDays(4), today))
    }

    @Test
    fun `the route is projected inside the drawing area`() {
        val route = listOf(
            RoutePoint(11.4557, 78.1856, isStay = true),
            RoutePoint(11.4700, 78.1900, isStay = false),
            RoutePoint(11.4900, 78.2100, isStay = true),
        )

        val projected = projectToUnitSquare(route)

        assertEquals(3, projected.size)
        projected.forEach { (x, y) ->
            assertTrue("x out of range: $x", x in 0.0..1.0)
            assertTrue("y out of range: $y", y in 0.0..1.0)
        }
    }

    @Test
    fun `north is drawn above south`() {
        val route = listOf(
            RoutePoint(11.40, 78.18, isStay = true), // south
            RoutePoint(11.50, 78.18, isStay = true), // north
        )

        val (south, north) = projectToUnitSquare(route)

        assertTrue("Northern point must be higher on screen", north.second < south.second)
    }

    @Test
    fun `a day spent at one place still projects without dividing by zero`() {
        val route = List(3) { RoutePoint(11.4557, 78.1856, isStay = true) }

        val projected = projectToUnitSquare(route)

        assertEquals(3, projected.size)
        projected.forEach { (x, y) ->
            assertTrue(x.isFinite() && y.isFinite())
            assertEquals(0.5, x, 0.0001)
            assertEquals(0.5, y, 0.0001)
        }
    }

    @Test
    fun `an empty route projects to nothing`() {
        assertEquals(emptyList<Pair<Double, Double>>(), projectToUnitSquare(emptyList()))
    }
}
