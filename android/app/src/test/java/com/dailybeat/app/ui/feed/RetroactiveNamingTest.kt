package com.dailybeat.app.ui.feed

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Naming a spot has to relabel the stays already recorded there. Applying the name only to
 * future captures made the action look like it had done nothing.
 */
class RetroactiveNamingTest {

    private val date: LocalDate = LocalDate.of(2026, 9, 7)
    private val lat = 11.286545
    private val lon = 78.337674

    private fun stayAt(lat: Double, lon: Double, storedName: String?) = LocationVisit(
        startMs = 1_757_000_000_000L,
        endMs = 1_757_003_600_000L,
        latitude = lat,
        longitude = lon,
        placeName = storedName,
        address = "Somewhere, Tamil Nadu",
        visitType = "dwell",
    )

    @Test
    fun `a stay already recorded takes the name the officer saved`() {
        val visits = listOf(stayAt(lat, lon, "Solakadu - Semmedu - Vilaram - Semmedu Road"))
        val places = listOf(Place(id = 1, name = "Rasipuram Police Station", latitude = lat, longitude = lon, radiusM = 150))

        val item = DayFeedBuilder.build(date, visits, null, places)

        assertEquals("Rasipuram Police Station", item.stays.single().name)
    }

    @Test
    fun `GPS drift within the saved radius still matches`() {
        val visits = listOf(stayAt(lat + 0.0006, lon - 0.0004, "Some Road"))
        val places = listOf(Place(id = 1, name = "Rasipuram Police Station", latitude = lat, longitude = lon, radiusM = 150))

        assertEquals("Rasipuram Police Station", DayFeedBuilder.build(date, visits, null, places).stays.single().name)
    }

    @Test
    fun `a stay somewhere else keeps its own name`() {
        val visits = listOf(stayAt(11.46, 78.19, "Government Bunglow"))
        val places = listOf(Place(id = 1, name = "Rasipuram Police Station", latitude = lat, longitude = lon, radiusM = 150))

        assertEquals("Government Bunglow", DayFeedBuilder.build(date, visits, null, places).stays.single().name)
    }

    @Test
    fun `with no saved places the map name is still used`() {
        val visits = listOf(stayAt(lat, lon, "Government Bunglow"))

        assertEquals("Government Bunglow", DayFeedBuilder.build(date, visits, null).stays.single().name)
    }
}
