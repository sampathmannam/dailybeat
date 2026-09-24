package com.dailybeat.app.ui.components

import org.junit.Assert.*
import org.junit.Test

class JourneySnapshotGridTest {
    @Test fun `zero negative and nonfinite viewports are not drawn`() {
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertNull(journeySnapshotGridSpacing(720f, invalid, 16f))
            assertNull(journeySnapshotGridSpacing(invalid, 400f, 16f))
            assertNull(journeySnapshotGridSpacing(720f, 400f, invalid))
        }
    }

    @Test fun `tiny height always has positive minimum spacing`() {
        for (height in listOf(Float.MIN_VALUE, 0.001f, 1f)) {
            val spacing = requireNotNull(journeySnapshotGridSpacing(720f, height, 16f))
            assertTrue(spacing.isFinite())
            assertTrue(spacing >= 16f)
            assertTrue(720f / spacing <= JOURNEY_GRID_MAX_LINES + 1f)
        }
    }

    @Test fun `extremely wide viewport has a bounded finite grid`() {
        for (width in listOf(100_000f, Float.MAX_VALUE)) {
            val spacing = requireNotNull(journeySnapshotGridSpacing(width, 1f, 16f))
            assertTrue(spacing.isFinite() && spacing > 0f)
            assertTrue(width / spacing <= JOURNEY_GRID_MAX_LINES + 1f)
            assertTrue((JOURNEY_GRID_MAX_LINES * spacing).isFinite())
        }
    }

    @Test fun `normal viewport retains the existing quarter-height grid`() {
        assertEquals(100f, requireNotNull(journeySnapshotGridSpacing(720f, 400f, 16f)), 0f)
    }
}
