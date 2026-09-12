package com.dailybeat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `DayFeedBuilder` has always counted capture gaps, and the count reached the UI state and stopped
 * there — so a day with a 40-minute hole in it presented its distance and tracked time with
 * exactly the same confidence as a fully captured day. These tests pin what may now be claimed.
 */
class CaptureCoverageTest {

    @Test
    fun `a captured day with no gaps makes the positive claim`() {
        // "No capture gaps" has to be said out loud. Saying nothing reads as "complete" anyway,
        // but without the officer being able to tell the difference from "we never checked".
        assertEquals(
            CaptureCoverage.Complete,
            captureCoverage(gapCount = 0, hasCapture = true),
        )
    }

    @Test
    fun `gaps are reported with their count`() {
        assertEquals(CaptureCoverage.Gaps(1), captureCoverage(gapCount = 1, hasCapture = true))
        assertEquals(CaptureCoverage.Gaps(4), captureCoverage(gapCount = 4, hasCapture = true))
    }

    @Test
    fun `a day with nothing captured claims neither completeness nor gaps`() {
        // Before the first fix of the morning, "No capture gaps" would be a lie of omission: there
        // is no record yet for it to be a true statement about.
        assertEquals(
            CaptureCoverage.Unknown,
            captureCoverage(gapCount = 0, hasCapture = false),
        )
    }

    @Test
    fun `an empty day never inherits a stale gap count`() {
        assertEquals(
            CaptureCoverage.Unknown,
            captureCoverage(gapCount = 3, hasCapture = false),
        )
    }

    @Test
    fun `a negative count cannot fabricate a gap`() {
        assertEquals(
            CaptureCoverage.Complete,
            captureCoverage(gapCount = -1, hasCapture = true),
        )
    }
}
