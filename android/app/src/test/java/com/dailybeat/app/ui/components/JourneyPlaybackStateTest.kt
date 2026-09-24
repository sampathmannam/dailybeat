package com.dailybeat.app.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.ui.feed.DayFeedBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class JourneyPlaybackStateTest {
    private val route = JourneyMapModel.fromPoints(listOf(
        JourneyPoint(100L, 11.45, 78.18, "transit"),
        JourneyPoint(200L, 11.46, 78.19, "transit"),
        JourneyPoint(300L, 11.47, 78.20, "transit"),
    ))

    @Test fun `local route can replay without map readiness but one point or only stop markers cannot`() {
        assertTrue(route.canReplay)
        assertFalse(JourneyMapModel.fromPoints(route.points.take(1)).canReplay)
        assertFalse(JourneyMapModel.fromPoints(route.points.map { it.copy(drawsRoute = false) }).canReplay)
        assertFalse(JourneyMapModel.fromPoints(emptyList()).canReplay)
    }

    @Test fun `new fixes and changed stop names cannot restart the active replay`() = runBlocking {
        val state = JourneyPlaybackState()
        state.toggle(route)
        assertTrue(state.isPlaying)
        assertEquals(0f, state.progress.value)
        val updated = JourneyMapModel.fromPoints(route.points.map { it.copy(stopLabel = "Corrected place") } +
            JourneyPoint(400L, 11.48, 78.21, "transit"))
        state.acceptModel(updated)
        assertSame(route, state.visibleModel(updated))
        assertTrue(state.isPlaying)
        state.seek(updated, 0.5f)
        assertFalse(state.isPlaying)
        assertEquals(0.5f, state.progress.value)
        state.toggle(updated)
        assertTrue(state.isPlaying)
        assertSame(route, state.visibleModel(updated))
        assertEquals(0.5f, state.progress.value)
    }

    @Test fun `completed playback releases snapshot and next replay uses latest geometry`() = runBlocking {
        val state = JourneyPlaybackState()
        val updated = JourneyMapModel.fromPoints(route.points + JourneyPoint(400L, 11.48, 78.21, "transit"))
        state.toggle(route)
        withTimeout(5_000L) { withContext(TestFrameClock() + durationScale(1f)) { state.playToEnd() } }
        assertEquals(1f, state.progress.value)
        assertFalse(state.isPlaying)
        assertSame(updated, state.visibleModel(updated))
        state.toggle(updated)
        assertEquals(0f, state.progress.value)
        assertSame(updated, state.visibleModel(updated))
    }

    @Test fun `erase hidden point replacement or different day immediately drops old replay`() = runBlocking {
        val replacements = listOf(
            JourneyMapModel.fromPoints(emptyList()),
            JourneyMapModel.fromPoints(route.points.drop(1)),
            JourneyMapModel.fromPoints(route.points.map { it.copy(latitude = it.latitude + 0.1) }),
            JourneyMapModel.fromPoints(route.points.map { it.copy(startMs = it.startMs + 86_400_000L) }),
        )
        for (replacement in replacements) {
            val state = JourneyPlaybackState()
            state.toggle(route)
            // Rendering must use new data even before the reconciliation effect has run.
            assertSame(replacement, state.visibleModel(replacement))
            state.acceptModel(replacement)
            assertFalse(state.isPlaying)
            assertEquals(1f, state.progress.value)
            assertSame(replacement, state.visibleModel(replacement))
        }
    }

    @Test fun `remove animations completes immediately while manual seek remains usable`() = runBlocking {
        val state = JourneyPlaybackState()
        val clock = TestFrameClock()
        state.toggle(route)
        withTimeout(5_000L) { withContext(clock + durationScale(0f)) { state.playToEnd() } }
        assertTrue("Reduced motion must not run a timed replay", clock.frames <= 2)
        assertEquals(1f, state.progress.value)
        assertFalse(state.isPlaying)
        state.seek(route, 0.25f)
        assertEquals(0.25f, state.progress.value)
        assertFalse(state.isPlaying)
        assertEquals(150L, state.visibleModel(route).atPlaybackProgress(state.progress.value).points.last().startMs)
    }

    @Test fun `pause retains progress and invalid seek values cannot corrupt a frame`() = runBlocking {
        val state = JourneyPlaybackState()
        state.seek(route, 0.4f)
        state.toggle(route)
        state.pause()
        assertFalse(state.isPlaying)
        assertEquals(0.4f, state.progress.value)
        state.seek(route, Float.NaN)
        assertEquals(0.4f, state.progress.value)
    }

    @Test fun `fallback replay uses full-route projection rather than zooming each frame`() {
        val projection = JourneyProjection(route)
        val start = projection.project(route.atPlaybackProgress(0f).points.last())
        val middle = projection.project(route.atPlaybackProgress(0.5f).points.last())
        val end = projection.project(route.atPlaybackProgress(1f).points.last())
        assertTrue(start.first < middle.first && middle.first < end.first)
        assertTrue(start.second > middle.second && middle.second > end.second)
    }

    @Test fun `antimeridian replay stays near date line and capture gaps are not interpolated`() {
        val crossing = JourneyMapModel.fromPoints(listOf(
            JourneyPoint(100L, 10.0, 179.0, "transit"),
            JourneyPoint(200L, 10.0, -179.0, "transit"),
        ))
        assertEquals(180.0, crossing.atPlaybackProgress(0.5f).points.last().longitude, 0.0001)
        val gapped = JourneyMapModel.fromPoints(crossing.points.mapIndexed { index, point ->
            point.copy(startsAfterGap = index == 1)
        })
        assertEquals(listOf(100L), gapped.atPlaybackProgress(0.5f).points.map { it.startMs })
    }

    @Test fun `downsampling recomputation safely invalidates rather than retaining removed geometry`() = runBlocking {
        val day = LocalDate.of(2026, 9, 22)
        val breadcrumbs = (0..3500).map { index ->
            LocationBreadcrumb(timestampMs = 1_790_064_000_000L + index * 1000L,
                latitude = 11.45 + index * 0.000001, longitude = 78.18, accuracyM = 10f)
        }
        fun mapped(points: List<LocationBreadcrumb>) = JourneyMapModel.fromRoute(
            DayFeedBuilder.build(day, emptyList(), null, breadcrumbs = points).route,
        )
        val beforeCap = mapped(breadcrumbs.take(3500))
        val afterAppend = mapped(breadcrumbs)
        assertEquals(3500, beforeCap.points.size)
        assertEquals(3500, afterAppend.points.size)
        assertFalse(afterAppend.preservesReplayOf(beforeCap))
        val state = JourneyPlaybackState()
        state.toggle(beforeCap)
        state.acceptModel(afterAppend)
        assertFalse(state.isPlaying)
        assertEquals(1f, state.progress.value)
        assertSame(afterAppend, state.visibleModel(afterAppend))
    }

    private fun durationScale(value: Float) = object : MotionDurationScale {
        override val scaleFactor = value
    }

    private class TestFrameClock : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frames++
            return onFrame(frames * 16_000_000L)
        }
    }
}
