package com.dailybeat.app.ui.patrol

import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PatrolGeographicRouteMapStateTest {

    @Test
    fun `whole map attempt expires even before a map-ready callback`() = runTest {
        val expired = async {
            patrolMapAttemptExpired(timeoutMs = 20_000L, isRendered = { false })
        }

        advanceTimeBy(19_999L)
        runCurrent()
        assertFalse(expired.isCompleted)

        advanceTimeBy(1L)
        runCurrent()
        assertTrue(expired.await())
    }

    @Test
    fun `completed first render keeps the attempt successful at its deadline`() = runTest {
        var rendered = false
        val expired = async {
            patrolMapAttemptExpired(timeoutMs = 20_000L, isRendered = { rendered })
        }

        advanceTimeBy(10_000L)
        rendered = true
        advanceTimeBy(10_000L)
        runCurrent()

        assertFalse(expired.await())
    }
}
