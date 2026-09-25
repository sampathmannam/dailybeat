package com.dailybeat.app.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JourneyMapRasterizerTest {
    private val app get() = ApplicationProvider.getApplicationContext<DailyBeatApp>()
    private val model = JourneyMapModel.fromPoints(listOf(
        JourneyPoint(100, 11.45, 78.18, "transit"),
        JourneyPoint(200, 11.46, 78.19, "transit"),
        JourneyPoint(300, 11.47, 78.20, "transit"),
    ))
    // Wider than one tile so at least two requests are always exercised.
    private val size = IntSize(400, 188)
    private fun tile() = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(Color.LTGRAY) }

    @Test fun `successful tiles return a complete map and recycle decoded tiles`() = runTest {
        val tiles = mutableListOf<Bitmap>()
        val result = renderJourneyMapRaster(app, model, size, 1f) { _, _, _ -> tile().also(tiles::add) }
        try {
            assertTrue(result.complete)
            assertFalse(result.bitmap.isRecycled)
            assertTrue(tiles.size >= 2)
            assertTrue(tiles.all { it.isRecycled })
        } finally { result.bitmap.recycle() }
    }

    @Test fun `overall tile timeout keeps successful tiles instead of discarding the map`() = runTest {
        var requests = 0
        val first = tile()
        val result = renderJourneyMapRaster(app, model, size, 1f, tileBudgetMillis = 100) { _, _, _ ->
            if (requests++ == 0) first else { delay(1_000); tile() }
        }
        try {
            assertFalse(result.complete)
            assertFalse(result.bitmap.isRecycled)
            assertTrue(first.isRecycled)
            assertEquals(2, requests)
        } finally { result.bitmap.recycle() }
    }

    @Test fun `one stalled tile does not starve subsequent visible tiles`() = runTest {
        var requests = 0
        val result = renderJourneyMapRaster(app, model, size, 1f) { _, _, _ ->
            if (requests++ == 0) delay(10_000)
            tile()
        }
        try {
            assertFalse(result.complete)
            assertTrue(requests >= 2)
        } finally { result.bitmap.recycle() }
    }

    @Test fun `missing tile keeps remaining map but marks it retryable`() = runTest {
        var requests = 0
        val result = renderJourneyMapRaster(app, model, size, 1f) { _, _, _ ->
            if (requests++ == 0) null else tile()
        }
        try { assertFalse(result.complete) } finally { result.bitmap.recycle() }
    }

    @Test fun `unavailable tiles report failure for the local route fallback`() = runTest {
        try {
            renderJourneyMapRaster(app, model, size, 1f) { _, _, _ -> null }
            fail("No basemap tiles must not be reported as a ready map")
        } catch (_: IOException) { }
    }

    @Test fun `outer cancellation is never mistaken for a recoverable tile timeout`() = runTest {
        val first = tile()
        var requests = 0
        try {
            withTimeout(100) {
                renderJourneyMapRaster(app, model, size, 1f) { _, _, _ ->
                    if (requests++ == 0) first else { delay(1_000); tile() }
                }
            }
            fail("A disposed card must cancel, not return a bitmap")
        } catch (_: CancellationException) { }
        assertTrue(first.isRecycled)
        assertEquals(2, requests)
    }
}
