package com.dailybeat.app.ui.feed

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.LocationBreadcrumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class DayFeedBuilderTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 6)
    private val dayStart = 1_757_120_400_000L

    private fun minutes(count: Long) = TimeUnit.MINUTES.toMillis(count)

    private fun stay(
        name: String?,
        fromMinute: Long,
        toMinute: Long,
        lat: Double = 11.4557,
        lon: Double = 78.1856,
        address: String? = "Salem Road, Rasipuram, Tamil Nadu",
    ) = LocationVisit(
        startMs = dayStart + minutes(fromMinute),
        endMs = dayStart + minutes(toMinute),
        latitude = lat,
        longitude = lon,
        placeName = name,
        address = address,
        visitType = "dwell",
    )

    @Test
    fun `a stay reports the place name and how long it lasted`() {
        val item = DayFeedBuilder.build(date, listOf(stay("Rasipuram Police Station", 0, 40)), null)

        assertEquals(1, item.stayCount)
        assertEquals("Rasipuram Police Station", item.stays.single().name)
        assertEquals(40, item.stays.single().durationMinutes)
    }

    @Test
    fun `an unnamed stay falls back to the first part of its address`() {
        val item = DayFeedBuilder.build(date, listOf(stay(null, 0, 20)), null)

        assertEquals("Salem Road", item.stays.single().name)
    }

    @Test
    fun `a stay with neither name nor address is still listed`() {
        val item = DayFeedBuilder.build(date, listOf(stay(null, 0, 20, address = null)), null)

        assertEquals("Unnamed place", item.stays.single().name)
    }

    @Test
    fun `transit segments shape the route but are not listed as stays`() {
        val visits = listOf(
            stay("Station", 0, 40),
            LocationVisit(
                startMs = dayStart + minutes(40),
                endMs = dayStart + minutes(55),
                latitude = 11.47,
                longitude = 78.19,
                address = "En route",
                visitType = "transit",
            ),
            stay("Court", 55, 120, lat = 11.49, lon = 78.20),
        )

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(listOf("Station", "Court"), item.stays.map { it.name })
        assertEquals(3, item.route.size)
        assertEquals(listOf(true, false, true), item.route.map { it.isStay })
    }

    @Test
    fun `distance follows the order the day was travelled`() {
        val visits = listOf(
            stay("A", 0, 30, lat = 11.4557, lon = 78.1856),
            stay("B", 40, 80, lat = 11.4647, lon = 78.1856), // ~1 km north
        )

        val km = DayFeedBuilder.build(date, visits, null).distanceKm

        assertTrue("Expected about 1 km, got $km", km in 0.9..1.1)
    }

    @Test
    fun `an impossible gps teleport is left out of the route and distance`() {
        val visits = listOf(
            stay("Station", 0, 30, lat = 11.4557, lon = 78.1856),
            stay("GPS outlier", 31, 32, lat = -33.8688, lon = 151.2093),
            stay("Court", 40, 70, lat = 11.4647, lon = 78.1856),
        )

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(listOf(11.4557, 11.4647), item.route.map { it.latitude })
        assertTrue("Expected the nearby journey only, got ${item.distanceKm} km", item.distanceKm in 0.9..1.1)
        assertFalse(item.stays.single { it.name == "GPS outlier" }.canBeNamed)
    }

    @Test
    fun `an impossible first gps fix does not hide the later local route`() {
        val visits = listOf(
            stay("Bad first fix", 0, 1, lat = -33.8688, lon = 151.2093),
            stay("Station", 10, 30, lat = 11.4557, lon = 78.1856),
            stay("Court", 40, 70, lat = 11.4647, lon = 78.1856),
        )

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(listOf(11.4557, 11.4647), item.route.map { it.latitude })
        assertTrue("Expected the later local journey, got ${item.distanceKm} km", item.distanceKm in 0.9..1.1)
    }

    @Test
    fun `a plausible long journey remains in the route`() {
        val visits = listOf(
            stay("Start", 0, 30, lat = 11.4557, lon = 78.1856),
            stay("Destination", 210, 240, lat = 13.0827, lon = 80.2707),
        )

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(2, item.route.size)
        assertTrue("Expected a long but plausible journey, got ${item.distanceKm} km", item.distanceKm > 250)
    }

    @Test
    fun `time out spans the first and last thing captured`() {
        val visits = listOf(stay("A", 30, 60), stay("B", 120, 200, lat = 11.47))

        assertEquals(170, DayFeedBuilder.build(date, visits, null).activeMinutes)
    }

    @Test
    fun `visits recorded out of order are still read chronologically`() {
        val visits = listOf(stay("Later", 120, 150, lat = 11.47), stay("Earlier", 10, 40))

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(listOf("Earlier", "Later"), item.stays.map { it.name })
        assertEquals(dayStart + minutes(10), item.firstSeenMs)
        assertEquals(dayStart + minutes(150), item.lastSeenMs)
    }

    @Test
    fun `a placeholder coordinate is kept out of the drawn route`() {
        val visits = listOf(stay("Real", 0, 30), stay("Bogus", 40, 60, lat = 0.0, lon = 0.0))

        val item = DayFeedBuilder.build(date, visits, null)

        assertEquals(2, item.stayCount)
        assertEquals(1, item.route.size)
        assertEquals(0.0, item.distanceKm, 0.0001)
        assertFalse(item.stays.last().canBeNamed)
    }

    @Test
    fun `a long diary is previewed rather than shown whole`() {
        val item = DayFeedBuilder.build(date, listOf(stay("A", 0, 30)), "x".repeat(500))

        assertTrue(item.diaryPreview!!.length < 250)
        assertTrue(item.diaryPreview!!.endsWith("…"))
    }

    @Test
    fun `breadcrumbs drive route distance and expose capture gaps without inventing distance`() {
        val points = listOf(
            LocationBreadcrumb(timestampMs = dayStart, latitude = 11.4557, longitude = 78.1856, accuracyM = 20f),
            LocationBreadcrumb(timestampMs = dayStart + minutes(2), latitude = 11.4647, longitude = 78.1856, accuracyM = 25f),
            LocationBreadcrumb(timestampMs = dayStart + minutes(32), latitude = 11.5000, longitude = 78.1856, accuracyM = 30f),
            LocationBreadcrumb(timestampMs = dayStart + minutes(34), latitude = 11.5090, longitude = 78.1856, accuracyM = 100f, quality = "approximate"),
        )

        val item = DayFeedBuilder.build(date, emptyList(), null, breadcrumbs = points)

        assertEquals(1, item.captureGapCount)
        assertTrue(item.route.single { it.timestampMs == dayStart + minutes(32) }.startsAfterGap)
        assertTrue("Only measured segments should count, got ${item.distanceKm}", item.distanceKm in 1.8..2.2)
        assertTrue(item.distanceEstimated)
    }

    @Test
    fun `a day with nothing captured is marked empty`() {
        val item = DayFeedBuilder.build(date, emptyList(), "   ")

        assertTrue(item.isEmpty)
        assertEquals(0, item.activeMinutes)
    }

    @Test
    fun `a day with only a diary is not empty`() {
        assertFalse(DayFeedBuilder.build(date, emptyList(), "Wrote this by hand.").isEmpty)
    }

    @Test
    fun `an open ended stay never reports negative time`() {
        val visit = LocationVisit(
            startMs = dayStart + minutes(60),
            endMs = 0,
            latitude = 11.4557,
            longitude = 78.1856,
            placeName = "Still here",
            visitType = "dwell",
        )

        assertEquals(0, DayFeedBuilder.build(date, listOf(visit), null).stays.single().durationMinutes)
    }
}
