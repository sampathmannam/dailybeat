package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitLabelsTest {
    private val visit = LocationVisit(
        startMs = 1, endMs = 2, latitude = 11.4557, longitude = 78.1856,
        placeName = "Unnamed place", address = "Paramathi Road, Namakkal",
    )

    @Test fun `machine placeholders are rejected but meaningful longer names are preserved`() {
        listOf(null, "", "  Unnamed PLACE ", "Unknown location", "En route", "Travel recorded").forEach {
            assertNull(VisitLabels.usable(it))
        }
        assertEquals("Transit Cafe", VisitLabels.usable(" Transit Cafe "))
    }

    @Test fun `the full or compact address is available behind an old placeholder`() {
        assertEquals("Paramathi Road, Namakkal", VisitLabels.name(visit))
        assertEquals("Paramathi Road", VisitLabels.name(visit, shortAddress = true))
    }

    @Test fun `meaningful manual corrections override the surrounding saved place`() {
        val saved = Place(name = "Camp office", latitude = visit.latitude, longitude = visit.longitude)
        assertEquals(
            "Transit Cafe",
            VisitLabels.name(visit.copy(placeName = "Transit Cafe", manuallyEdited = true), listOf(saved)),
        )
    }

    @Test fun `saved place names keep even an unusual literal name`() {
        val saved = Place(name = "Transit", latitude = visit.latitude, longitude = visit.longitude)
        assertEquals("Transit", VisitLabels.name(visit, listOf(saved)))
    }

    @Test fun `hiding then restoring an unnamed visit does not promote its placeholder`() {
        val hidden = visit.copy(hidden = true, manuallyEdited = true)
        val restored = hidden.copy(hidden = false, manuallyEdited = true)
        val saved = Place(name = "Camp office", latitude = visit.latitude, longitude = visit.longitude)

        assertEquals("Paramathi Road, Namakkal", VisitLabels.name(restored))
        assertEquals("Camp office", VisitLabels.name(restored, listOf(saved)))
        assertEquals("Unnamed place", restored.placeName)
    }

    @Test fun `invalid or absent coordinates cannot match a saved place`() {
        val saved = listOf(Place(name = "Not observed", latitude = 0.0, longitude = 0.0, radiusM = 1000))
        assertNull(VisitLabels.savedPlaceName(0.0, 0.0, saved))
        assertNull(VisitLabels.savedPlaceName(Double.NaN, 0.0, saved))
        assertNull(VisitLabels.savedPlaceName(null, null, saved))
        assertNull(VisitLabels.savedPlaceName(91.0, 181.0, saved))
    }

    @Test fun `a genuine unknown stays unknown`() {
        assertNull(VisitLabels.name(visit.copy(address = "Unnamed place")))
        assertEquals("Travel recorded", VisitLabels.momentText(transit = true, name = null))
        assertEquals("Stay recorded", VisitLabels.momentText(transit = false, name = null))
    }

    @Test fun `an unnamed coordinate becomes a named approximate area not a venue`() {
        val unnamed = visit.copy(placeName = "Unknown location", address = null)
        assertEquals("Approx. area · Near Rasipuram", VisitLabels.displayName(unnamed))
        assertNull(VisitLabels.name(unnamed))
        assertTrue(VisitLabels.isFallback(VisitLabels.displayName(unnamed)))
        assertEquals("Unknown location", unnamed.placeName)
    }

    @Test fun `approximate names are locale stable and support southern western hemispheres`() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            assertEquals("Approx. area · Near Paine", VisitLabels.approximateLocation(-33.8688, -70.6693))
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun `missing and invalid fixes are stated explicitly not invented`() {
        listOf(null to null, 0.0 to 0.0, Double.NaN to 78.0, 11.0 to Double.POSITIVE_INFINITY, 91.0 to 181.0)
            .forEach { (lat, lon) -> assertNull(VisitLabels.approximateLocation(lat, lon)) }
        assertEquals("No GPS fix recorded", VisitLabels.displayName(visit.copy(latitude = 0.0, longitude = 0.0, address = null)))
        assertEquals("Paramathi Road, Namakkal", VisitLabels.displayName(visit))
    }

    @Test fun `historical raw coordinate names do not hide named areas or actual addresses`() {
        for (label in listOf("11.4557", "11.4557, 78.1856", "Location 11.4557,78.1856")) {
            assertNull(VisitLabels.usable(label))
            assertEquals("Approx. area · Near Rasipuram", VisitLabels.displayName(visit.copy(placeName = label, address = label)))
            assertEquals("Paramathi Road, Namakkal", VisitLabels.displayName(visit.copy(placeName = label)))
        }
        assertEquals("Building 11.4557", VisitLabels.usable("Building 11.4557"))
    }
}
