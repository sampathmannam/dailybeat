package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.*
import org.junit.Test

/** User-requested acceptance fixture: offline, named, approximate, never a claimed venue. */
class OfflineAreaLabelsTest {
    private val visit = LocationVisit(startMs = 1, endMs = 2, latitude = 11.4557,
        longitude = 78.1856, placeName = "Unnamed place")

    @Test fun `valid unnamed stop has an offline town reference without coordinates`() {
        assertEquals("Approx. area · Near Rasipuram", VisitLabels.displayName(visit))
        assertEquals("Approx. area · Near Namakkal", VisitLabels.approximateLocation(11.22126, 78.16524))
        assertEquals("Approx. area · Near Velur", VisitLabels.approximateLocation(11.10825, 78.00113))
    }

    @Test fun `remote ocean point has a distance reference rather than a false nearby town`() {
        val label = VisitLabels.approximateLocation(0.0, -140.0)!!
        assertTrue(label.startsWith("Approx. area · About "))
        assertTrue(label.contains(" km from "))
        assertFalse(label.contains("°"))
        assertFalse(label.contains("Near "))
    }

    @Test fun `both generations of fallback stay unconfirmed and cannot mask an address`() {
        for (label in listOf("Approx. area · Near Rasipuram", "Approx. location · 11.456°N, 78.186°E")) {
            assertTrue(VisitLabels.isApproximate(label))
            assertNull(VisitLabels.usable(label))
            assertEquals("Market Road", VisitLabels.displayName(visit.copy(placeName = label, address = "Market Road")))
        }
    }

    @Test fun `actual saved and corrected names win without mutating history`() {
        val places = listOf(Place(name = "My workshop", latitude = visit.latitude, longitude = visit.longitude))
        assertEquals("My workshop", VisitLabels.displayName(visit, places))
        assertEquals("Side entrance", VisitLabels.displayName(visit.copy(placeName = "Side entrance", manuallyEdited = true), places))
        assertEquals("Unnamed place", visit.placeName)
    }

    @Test fun `invalid fixes still cannot invent a location`() {
        listOf(null to null, 0.0 to 0.0, 91.0 to 78.0, 11.0 to 181.0,
            Double.NaN to 1.0, 1.0 to Double.POSITIVE_INFINITY).forEach { (lat, lon) ->
            assertNull(VisitLabels.approximateLocation(lat, lon))
        }
    }
}
