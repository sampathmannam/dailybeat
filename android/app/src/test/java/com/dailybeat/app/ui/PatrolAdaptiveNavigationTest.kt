package com.dailybeat.app.ui

import com.dailybeat.app.ui.patrol.shouldUsePatrolNavigationRail
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatrolAdaptiveNavigationTest {

    @Test
    fun `compact widths use bottom navigation and wide widths use rail`() {
        assertFalse(shouldUsePatrolNavigationRail(360f))
        assertFalse(shouldUsePatrolNavigationRail(719.9f))
        assertTrue(shouldUsePatrolNavigationRail(720f))
        assertTrue(shouldUsePatrolNavigationRail(1_280f))
    }
}
