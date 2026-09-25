package com.dailybeat.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.ui.components.*
import com.dailybeat.app.ui.feed.DayRouteThumbnail
import com.dailybeat.app.ui.feed.RoutePoint
import com.dailybeat.app.ui.theme.DailyBeatTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Uses synthetic places and isolated app settings only; no public tile requests. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class JourneyMapRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: DailyBeatApp
    private var previouslyOnline = false
    private val visible = mutableStateOf(true)
    private val route = listOf(
        RoutePoint(11.45, 78.18, false, 100),
        RoutePoint(11.46, 78.195, false, 200),
        RoutePoint(11.47, 78.20, false, 300),
    )
    private val model = JourneyMapModel.fromRoute(route)

    @Before fun prepare() {
        requireDisposableTestApp()
        app = ApplicationProvider.getApplicationContext()
        previouslyOnline = app.mapSettings.state.value.allowOnlineMaps
        app.mapSettings.setOnline(true)
    }

    @After fun cleanup() {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnUiThread { visible.value = false }
        compose.waitForIdle()
        app.mapSettings.setOnline(previouslyOnline)
    }

    private fun show(
        fontScale: Float = 1f,
        showRetryButton: Boolean = true,
        loader: suspend (Context, JourneyMapModel, IntSize, Float) -> JourneyMapRaster,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                DailyBeatTheme {
                    if (visible.value) JourneyMapSnapshot(
                        model, Modifier.width(320.dp).height(188.dp), rasterLoader = loader,
                        showRetryButton = showRetryButton,
                    )
                }
            }
        }
    }

    private suspend fun raster(context: Context, model: JourneyMapModel, size: IntSize, density: Float) =
        renderJourneyMapRaster(context, model, size, density) { _, _, _ ->
            Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        }

    @Test fun failedMapCanRetryWithoutLosingRouteAndDoesNotPoll() {
        val calls = AtomicInteger()
        val retry = CompletableDeferred<Unit>()
        show(fontScale = 2f) { context, journey, size, density ->
            if (calls.incrementAndGet() == 1) throw IOException("Simulated provider failure")
            retry.await()
            raster(context, journey, size, density)
        }
        compose.waitUntilExactlyOneExists(hasTestTag("journey_map_snapshot_retry"), 10_000)
        compose.mainClock.advanceTimeBy(60_000)
        compose.waitForIdle()
        assertEquals("Failure must not start a retry loop", 1, calls.get())
        compose.onNodeWithTag("journey_map_snapshot_retry").assertHeightIsAtLeast(48.dp)
        assertVisibleRoute("journey_map_snapshot")
        saveScreenshot("failed-local-route")
        compose.onNodeWithTag("journey_map_snapshot_retry").performClick()
        compose.waitUntil(10_000) { calls.get() == 2 }
        compose.onNodeWithTag("journey_map_snapshot_retry").assertIsNotEnabled()
        assertVisibleRoute("journey_map_snapshot")
        retry.complete(Unit)
        compose.waitUntilExactlyOneExists(hasTestTag("journey_map_snapshot_ready"), 10_000)
        compose.onNodeWithTag("journey_map_snapshot_retry").assertDoesNotExist()
        assertVisibleRoute("journey_map_snapshot")
        saveScreenshot("recovered-route")
    }

    @Test fun returnToForegroundRepairsFailedMapButDoesNotReloadHealthyMap() {
        val calls = AtomicInteger()
        show { context, journey, size, density ->
            if (calls.incrementAndGet() == 1) throw IOException("Offline")
            raster(context, journey, size, density)
        }
        compose.waitUntilExactlyOneExists(hasTestTag("journey_map_snapshot_retry"), 10_000)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntilExactlyOneExists(hasTestTag("journey_map_snapshot_ready"), 10_000)
        assertEquals(2, calls.get())
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(2, calls.get())
    }

    @Test fun fullMapCanOwnRecoveryWithoutDuplicateRetryButtons() {
        val calls = AtomicInteger()
        show(showRetryButton = false) { _, _, _, _ ->
            calls.incrementAndGet()
            throw IOException("No streets")
        }
        compose.waitUntil(10_000) { calls.get() == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("journey_map_snapshot_retry").assertDoesNotExist()
        assertVisibleRoute("journey_map_snapshot")
    }

    @Test fun partialMapStaysVisibleDuringRetryAndOnlineOptOutStopsLoading() {
        val calls = AtomicInteger()
        val pending = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        show { context, journey, size, density ->
            if (calls.incrementAndGet() == 1) raster(context, journey, size, density).copy(complete = false)
            else {
                try { pending.await() } finally { cancelled.complete(Unit) }
                raster(context, journey, size, density)
            }
        }
        compose.waitUntilExactlyOneExists(hasTestTag("journey_map_snapshot_retry"), 10_000)
        compose.onNodeWithTag("journey_map_snapshot_ready").assertExists()
        compose.onNodeWithTag("journey_map_snapshot_retry").performClick()
        compose.waitUntil(10_000) { calls.get() == 2 }
        compose.onNodeWithTag("journey_map_snapshot_ready").assertExists()
        app.mapSettings.setOnline(false)
        compose.waitUntil(10_000) { cancelled.isCompleted }
        compose.onNodeWithTag("journey_map_snapshot_retry").assertDoesNotExist()
        compose.onNodeWithTag("journey_map_snapshot_ready").assertDoesNotExist()
        assertVisibleRoute("journey_map_snapshot")
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(2, calls.get())
    }

    @Test fun todayAndDaysBothShowRecordedRouteWithOnlineMapsDisabled() {
        app.mapSettings.setOnline(false)
        val dark = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                DailyBeatTheme(darkTheme = dark.value) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        if (visible.value) Column(Modifier.width(320.dp)) {
                            JourneyRoutePreview(route, onOpenMap = {})
                            DayRouteThumbnail(route)
                        }
                    }
                }
            }
        }
        for (darkMode in listOf(false, true)) {
            compose.runOnIdle { dark.value = darkMode }
            assertVisibleRoute("today_journey_map")
            assertVisibleRoute("feed_route_map")
            compose.onNodeWithTag("today_journey_map_retry").assertDoesNotExist()
            compose.onNodeWithTag("feed_route_map_retry").assertDoesNotExist()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("Open full map", useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("Open map must not be squeezed into a column at 200% text", 1, layouts.single().lineCount)
            saveScreenshot("both-offline-200-percent-${if (darkMode) "dark" else "light"}")
        }
    }

    @Test fun optionalLiveStreetMapLoadsInBothCards() {
        assumeTrue("Explicit opt-in: public map providers are not a deterministic CI dependency",
            InstrumentationRegistry.getArguments().getString("liveMapChecks") == "true")
        compose.setContent {
            DailyBeatTheme {
                if (visible.value) Column(Modifier.width(360.dp)) {
                    JourneyRoutePreview(route, onOpenMap = {})
                    DayRouteThumbnail(route)
                }
            }
        }
        try {
            for (tag in listOf("today_journey_map_ready", "feed_route_map_ready")) {
                // Today is a clickable map card and merges its child semantics for TalkBack.
                compose.waitUntil(25_000) {
                    compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size == 1
                }
            }
        } finally { saveScreenshot("both-live-street-maps") }
        assertVisibleRoute("today_journey_map")
        assertVisibleRoute("feed_route_map")
    }

    private fun saveScreenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        try {
            val directory = File(app.getExternalFilesDir(null), "map-recovery").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun assertVisibleRoute(tag: String) {
        val bitmap = compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            .captureToImage().asAndroidBitmap()
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Saved yellow route must be drawn in $tag", pixels.count { pixel ->
                kotlin.math.abs(Color.red(pixel) - 238) < 5 &&
                    kotlin.math.abs(Color.green(pixel) - 215) < 5 &&
                    kotlin.math.abs(Color.blue(pixel) - 123) < 5
            } > 100)
        } finally { bitmap.recycle() }
    }
}
