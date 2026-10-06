package com.dailybeat.app.capture

import android.app.Application
import android.location.Location
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationSourceFreshnessTest {
    private class Source : LocationSource {
        var starts = 0
        var profile: ActiveCaptureProfile? = null
        lateinit var ready: () -> Unit
        lateinit var locations: (List<Location>) -> Unit
        override fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
                           onReady: () -> Unit, onError: (Exception) -> Unit) {
            starts++; this.profile = profile; ready = onReady; locations = onLocations
        }
        override fun stop() = Unit
        override suspend fun current(): Location? = null
    }
    private data class Deadline(val delay: Long, val action: () -> Unit, var cancelled: Boolean = false)
    private class Fixture {
        val primary = Source()
        val fallback = Source()
        val deadlines = mutableListOf<Deadline>()
        var deliveries = 0
        val source = RecoveringLocationSource(primary, fallback, { delay, action ->
            val deadline = Deadline(delay, action).also(deadlines::add)
            val cancel: () -> Unit = { deadline.cancelled = true }
            cancel
        })
        fun start(profile: ActiveCaptureProfile = ActiveCaptureProfile.MOVING) {
            source.start(profile, { deliveries++ }, {}, { throw it })
            primary.ready()
        }
    }

    @Test fun `a fresh callback renews the deadline and a queued old deadline is harmless`() {
        val f = Fixture(); f.start()
        val old = f.deadlines.last()
        f.primary.locations(listOf(Location("gps")))
        assertTrue(old.cancelled)
        old.action()
        assertEquals(0, f.fallback.starts)
        f.deadlines.last().action()
        assertEquals(1, f.fallback.starts)
        assertEquals(ActiveCaptureProfile.MOVING, f.fallback.profile)
        f.source.stop()
    }

    @Test fun `empty deliveries cannot postpone recovery`() {
        val f = Fixture(); f.start()
        val deadline = f.deadlines.last()
        f.primary.locations(emptyList())
        assertSame(deadline, f.deadlines.last())
        deadline.action()
        assertEquals(1, f.fallback.starts)
        f.source.stop()
    }

    @Test fun `recovery tries native GPS before the bounded burst expires`() {
        val f = Fixture(); f.start(ActiveCaptureProfile.RECOVERY)
        val deadline = f.deadlines.last()
        assertEquals(30_000L, deadline.delay)
        assertTrue(deadline.delay < CaptureRecoveryPolicy.MAX_BURST_MS)
        deadline.action()
        assertEquals(ActiveCaptureProfile.RECOVERY, f.fallback.profile)
        f.primary.locations(listOf(Location("gps")))
        assertEquals(0, f.deliveries)
        f.source.stop()
    }

    @Test fun `stopping a ready subscription cancels recovery and ignores late delivery`() {
        val f = Fixture(); f.start()
        val deadline = f.deadlines.last()
        f.source.stop()
        assertTrue(deadline.cancelled)
        deadline.action(); f.primary.locations(listOf(Location("gps")))
        assertEquals(0, f.fallback.starts)
        assertEquals(0, f.deliveries)
    }
}
