package com.dailybeat.app.ui.components

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The arithmetic behind `Modifier.readableContentWidth()`. Kept as pure functions so the rule is
 * provable on the JVM rather than only observable on a tablet.
 */
class ReadableContentWidthTest {

    private val cap = 840

    @Test
    fun `below the cap the content keeps the full available width`() {
        // A 360dp phone: the cap must never bind, or every phone screen would be inset.
        assertEquals(360, readableChildMaxWidth(availableWidth = 360, capWidth = cap))
        assertEquals(0, readableHorizontalOffset(availableWidth = 360, childWidth = 360))
    }

    @Test
    fun `at exactly the cap nothing is inset`() {
        assertEquals(cap, readableChildMaxWidth(availableWidth = cap, capWidth = cap))
        assertEquals(0, readableHorizontalOffset(availableWidth = cap, childWidth = cap))
    }

    @Test
    fun `above the cap the content is capped and centred`() {
        // A tablet pane left over after the navigation rail.
        assertEquals(cap, readableChildMaxWidth(availableWidth = 1200, capWidth = cap))
        // (1200 - 840) / 2 — equal gutters, so the column sits in the middle of the pane.
        assertEquals(180, readableHorizontalOffset(availableWidth = 1200, childWidth = cap))
    }

    @Test
    fun `an odd leftover width still produces a non-negative offset`() {
        assertEquals(180, readableHorizontalOffset(availableWidth = 1201, childWidth = cap))
    }

    @Test
    fun `unbounded width is passed through rather than collapsed to the cap`() {
        // Measuring with an infinite width budget happens inside horizontally scrollable parents.
        // Clamping there would silently hand the child a 840px budget it never asked for.
        assertEquals(
            Constraints.Infinity,
            readableChildMaxWidth(availableWidth = Constraints.Infinity, capWidth = cap),
        )
    }

    @Test
    fun `a child wider than the space it was given is never pushed off the left edge`() {
        assertEquals(0, readableHorizontalOffset(availableWidth = 300, childWidth = 400))
    }
}
