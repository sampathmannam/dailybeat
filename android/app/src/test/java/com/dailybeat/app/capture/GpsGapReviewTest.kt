package com.dailybeat.app.capture

import android.location.Location
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diagnostic reproduction only: unmodified v4.3.8 should currently fail this test. */
class GpsGapReviewTest {
    private class Source : LocationSource {
        var starts = 0
        lateinit var ready: () -> Unit
        override fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
                           onReady: () -> Unit, onError: (Exception) -> Unit) {
            starts++
            ready = onReady
        }
        override fun stop() = Unit
        override suspend fun current(): Location? = null
    }

    @Test fun `a ready provider that delivers no fixes must recover before a ten minute gap`() {
        data class Timer(val at: Long, val action: () -> Unit, var cancelled: Boolean = false)
        var now = 0L
        val timers = mutableListOf<Timer>()
        val primary = Source()
        val fallback = Source()
        val source = RecoveringLocationSource(primary, fallback, { delay, action ->
            val timer = Timer(now + delay, action)
            timers += timer
            val cancel: () -> Unit = { timer.cancelled = true }
            cancel
        })
        source.start(ActiveCaptureProfile.MOVING, {}, {}, { throw it })
        primary.ready()
        val deadline = 10 * 60_000L
        while (true) {
            val timer = timers.filter { !it.cancelled && it.at <= deadline }.minByOrNull { it.at }
                ?: break
            timers.remove(timer)
            now = timer.at
            timer.action()
        }
        now = deadline
        assertTrue("Ready registration with no fixes remained unrecovered for $now ms",
            primary.starts > 1 || fallback.starts > 0)
        source.stop()
    }
}
