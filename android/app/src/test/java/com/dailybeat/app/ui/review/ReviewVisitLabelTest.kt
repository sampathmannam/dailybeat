package com.dailybeat.app.ui.review

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.ui.feed.DayFeedBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReviewVisitLabelTest {
    private val visit = LocationVisit(
        id = 7, startMs = 1, endMs = 2, latitude = 11.4557, longitude = 78.1856,
        placeName = "Unnamed place", address = "Unnamed place",
    )

    @Test fun `review and rename seed reuse the saved name from the same built day`() {
        val day = DayFeedBuilder.build(
            LocalDate.of(2026, 9, 23), listOf(visit), null,
            listOf(Place(name = "Royal Oak", latitude = visit.latitude, longitude = visit.longitude)),
        )

        assertEquals("Royal Oak", reviewVisitLabel(visit, day.stays))
        assertEquals("Royal Oak", reviewRenameSeed(reviewVisitLabel(visit, day.stays)))
    }

    @Test fun `unknown review names remain blank in the rename field`() {
        assertEquals("Approx. area · Near Rasipuram", reviewVisitLabel(visit, emptyList()))
        assertEquals("", reviewRenameSeed(reviewVisitLabel(visit, emptyList())))
        assertEquals("", reviewRenameSeed(VisitLabels.UNAVAILABLE))
    }

    @Test fun `hidden rows keep their stored label without borrowing a stale visible stay`() {
        val visibleDay = DayFeedBuilder.build(
            LocalDate.of(2026, 9, 23), listOf(visit), null,
            listOf(Place(name = "Saved place", latitude = visit.latitude, longitude = visit.longitude)),
        )

        assertEquals(
            "Original correction",
            reviewVisitLabel(visit.copy(hidden = true, placeName = "Original correction", manuallyEdited = true), visibleDay.stays),
        )
    }
}
