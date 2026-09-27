package com.dailybeat.app

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.maps.LocalMapLease
import com.dailybeat.app.maps.MapPreferences
import com.dailybeat.app.ui.components.*
import com.dailybeat.app.ui.theme.DailyBeatTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real phone renderer, synthetic route, local style; never opens the production database. */
@RunWith(AndroidJUnit4::class)
class JourneyMapStartupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun missingBackgroundTilesDoNotHideInteractiveRoute() {
        requireDisposableTestApp()
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val previous = app.mapSettings.state.value.allowOnlineMaps
        app.mapSettings.setOnline(false)
        val directory = File(app.cacheDir, "map-startup-fixture").apply { mkdirs() }
        File(directory, "region.geojson").writeText(
            """{"type":"Polygon","coordinates":[[[76,8],[81,8],[81,14],[76,14],[76,8]]]}""")
        // A usable style with unavailable background tiles. Local route layers must still work.
        File(directory, "light.json").writeText("""{"version":8,"sources":{"streets":{
            "type":"raster","tiles":["https://example.invalid/{z}/{x}/{y}.png"],"tileSize":256}},
            "layers":[{"id":"background","type":"background","paint":{"background-color":"#eeeeee"}},
            {"id":"streets","type":"raster","source":"streets"}]}""")
        val visible = mutableStateOf(true)
        val route = mutableStateOf(JourneyMapModel.fromPoints(listOf(
            JourneyPoint(100, 11.4557, 78.1856, "transit"),
            JourneyPoint(200, 11.46, 78.19, "transit"),
        )))
        val lease = LocalMapLease(app.offlineMaps.catalog, directory) {}
        compose.mainClock.autoAdvance = false
        val started = android.os.SystemClock.elapsedRealtime()
        try {
            compose.setContent {
                DailyBeatTheme(darkTheme = false) {
                    if (visible.value) JourneyMapPreviewContent(route.value, Modifier.fillMaxSize(), {},
                        MapPreferences(allowOnlineMaps = false),
                        lease)
                }
            }
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("journey_map_ready").fetchSemanticsNodes().isNotEmpty()
            }
            android.util.Log.i("DailyBeatMapStartupTest", "first_interactive_ms=" +
                (android.os.SystemClock.elapsedRealtime() - started))
            compose.onNodeWithText("Preparing interactive map…").assertDoesNotExist()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                val bitmap = compose.onNodeWithTag("journey_map").captureToImage().asAndroidBitmap()
                try {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    val routeVisible = pixels.count { pixel ->
                        kotlin.math.abs(Color.red(pixel) - 238) < 5 &&
                            kotlin.math.abs(Color.green(pixel) - 215) < 5 &&
                            kotlin.math.abs(Color.blue(pixel) - 123) < 5
                    } > 100
                    if (routeVisible) {
                        val evidence = File(app.getExternalFilesDir(null), "map-startup").apply { mkdirs() }
                        File(evidence, "interactive-route.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    routeVisible
                } finally { bitmap.recycle() }
            }
            compose.onNodeWithTag("replay_route").assertIsEnabled().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                route.value = JourneyMapModel.fromPoints(route.value.points +
                    JourneyPoint(300, 11.465, 78.195, "transit"))
            }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("journey_map_ready").assertExists()
            compose.onNodeWithTag("journey_map_retry").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(12_100)
            // Missing tiles can settle as an SDK-complete (partial-background) frame. Require
            // bounded loading, not an error-only button after the SDK has already settled.
            // Keep the independent first-frame limit above at 5 s.
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText("Route ready. Street details are loading.")
                    .fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText("Preparing interactive map…").assertDoesNotExist()
            compose.onNodeWithTag("journey_map_ready").assertExists()
            compose.onNodeWithTag("journey_map_fallback").assertDoesNotExist()
        } finally {
            // Synthetic Compose tree only; never capture the user's notifications/other apps.
            runCatching { compose.onRoot().printToString().lineSequence().forEach {
                android.util.Log.i("DailyBeatMapStartupTest", it)
            } }
            compose.runOnUiThread { visible.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            app.mapSettings.setOnline(previous)
            directory.deleteRecursively()
        }
    }
}
