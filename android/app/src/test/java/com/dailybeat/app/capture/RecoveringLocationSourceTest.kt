package com.dailybeat.app.capture

import android.location.Location
import org.junit.Assert.*
import org.junit.Test

class RecoveringLocationSourceTest {
    private class Source : LocationSource {
        var starts = 0
        var stops = 0
        var thrown: Exception? = null
        lateinit var ready: () -> Unit
        lateinit var error: (Exception) -> Unit
        lateinit var locations: (List<Location>) -> Unit
        override fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
                           onReady: () -> Unit, onError: (Exception) -> Unit) {
            starts++
            ready = onReady; error = onError; locations = onLocations
            thrown?.let { throw it }
        }
        override fun stop() { stops++ }
        override suspend fun current(): Location? = null
    }
    private class Fixture {
        val primary = Source()
        val fallback = Source()
        val deadlines = mutableListOf<() -> Unit>()
        var cancellations = 0
        var ready = 0
        var batches = 0
        val errors = mutableListOf<Exception>()
        val reasons = mutableListOf<Exception>()
        val source = RecoveringLocationSource(primary, fallback, { delay, action ->
            assertEquals(5_000L, delay)
            deadlines += action
            val cancel: () -> Unit = { cancellations++ }
            cancel
        }, reasons::add)
        fun start() = source.start(ActiveCaptureProfile.MOVING, { batches++ }, { ready++ }, errors::add)
    }

    @Test fun `hung provider falls back once and ignores late primary callbacks`() {
        val f = Fixture(); f.start()
        assertEquals(0, f.fallback.starts)
        f.deadlines.single().invoke()
        f.primary.ready(); f.primary.locations(emptyList())
        f.primary.error(IllegalStateException())
        assertEquals(1, f.fallback.starts)
        assertEquals(0, f.ready); assertEquals(0, f.batches)
        f.fallback.ready(); f.fallback.locations(emptyList())
        assertEquals(1, f.ready); assertEquals(1, f.batches)
        assertTrue(f.errors.isEmpty())
        assertTrue(f.reasons.single() is java.util.concurrent.TimeoutException)
    }

    @Test fun `successful primary cancels startup deadline`() {
        val f = Fixture(); f.start(); f.primary.ready()
        assertEquals(1, f.cancellations)
        assertEquals(1, f.ready)
        assertEquals(0, f.fallback.starts)
    }

    @Test fun `provider failure falls back immediately`() {
        val f = Fixture(); f.start()
        val reason = IllegalStateException("Provider restarted")
        f.primary.error(reason)
        assertSame(reason, f.reasons.single())
        assertEquals(1, f.fallback.starts)
        assertEquals(1, f.cancellations)
    }

    @Test fun `synchronous provider failure also falls back`() {
        val f = Fixture(); f.primary.thrown = IllegalStateException()
        f.start()
        assertEquals(1, f.fallback.starts)
        assertTrue(f.errors.isEmpty())
    }

    @Test fun `stopped capture cannot be restarted by timeout or callbacks`() {
        val f = Fixture(); f.start(); f.source.stop()
        f.deadlines.single().invoke(); f.primary.ready(); f.primary.locations(emptyList())
        assertEquals(0, f.ready); assertEquals(0, f.batches)
        assertEquals(0, f.fallback.starts)
        assertTrue(f.primary.stops >= 2 && f.fallback.stops >= 2)
    }

    @Test fun `old generation cannot start fallback or deliver after restart`() {
        val f = Fixture(); f.start()
        val oldReady = f.primary.ready; val oldLocations = f.primary.locations
        f.start(); f.deadlines.first().invoke(); oldReady(); oldLocations(emptyList())
        assertEquals(0, f.fallback.starts); assertEquals(0, f.ready); assertEquals(0, f.batches)
        f.primary.ready()
        assertEquals(1, f.ready)
    }

    @Test fun `fallback failure surfaces once and later callbacks are ignored after stop`() {
        val f = Fixture(); f.fallback.thrown = SecurityException("Permission revoked")
        f.start(); f.deadlines.single().invoke()
        assertSame(f.fallback.thrown, f.errors.single())
        f.source.stop(); f.fallback.ready(); f.fallback.locations(emptyList())
        assertEquals(0, f.ready); assertEquals(0, f.batches)
    }
}
