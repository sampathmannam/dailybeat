package com.dailybeat.app

import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import com.dailybeat.app.maps.LocalMapLease
import com.dailybeat.app.maps.MapPreferences
import com.dailybeat.app.ui.components.JourneyMapPreviewContent
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.components.JourneyPoint
import com.dailybeat.app.ui.theme.DailyBeatTheme
import com.dailybeat.app.maps.prepareOfflineMapStyle
import kotlinx.coroutines.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

/** Real local PMTiles/styles/glyphs, exercised in both online and airplane-mode CI lanes. */
@RunWith(AndroidJUnit4::class)
class NativeOfflineMapTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var app: DailyBeatApp
    private lateinit var directory: File
    private var view: MapView? = null
    private var previouslyOnline = true
    private val httpRequests = AtomicInteger()

    @Before fun prepare() {
        requireDisposableTestApp()
        app = ApplicationProvider.getApplicationContext()
        previouslyOnline = app.mapSettings.state.value.allowOnlineMaps
        app.mapSettings.setOnline(false)
        directory = File(app.cacheDir, "offline-render-fixture").apply { deleteRecursively(); mkdirs() }
        val args = InstrumentationRegistry.getArguments()
        val full = args.getString("fullMapDirectory")
        val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
        if (full == null) {
            testAssets.open("offline-map/tamil-nadu.pmtiles").use { input ->
                File(directory, "tamil-nadu.pmtiles").outputStream().use(input::copyTo)
            }
        } else {
            File(full, "tamil-nadu.pmtiles").copyTo(File(directory, "tamil-nadu.pmtiles"), overwrite = true)
        }
        val resources = if (full == null) testAssets.open("offline-map/resources.zip") else File(full, "resources.zip").inputStream()
        ZipInputStream(resources).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val target = File(directory, entry.name)
                require(target.canonicalPath.startsWith(directory.canonicalPath + "/"))
                if (entry.isDirectory) { target.mkdirs(); continue }
                target.parentFile!!.mkdirs()
                target.outputStream().use(zip::copyTo)
            }
        }
    }

    @After fun cleanup() {
        compose.runOnUiThread { view?.let { it.onPause(); it.onStop(); it.onDestroy() }; view = null }
        compose.runOnUiThread { app.mapNetwork.installNativeClient() }
        app.mapSettings.setOnline(previouslyOnline)
        directory.deleteRecursively()
    }

    @Test fun invalidRasterImagesAreRejected() {
        assertNull(com.dailybeat.app.ui.components.decodeMapTile("not an image".toByteArray()))
        val bitmap = android.graphics.Bitmap.createBitmap(1025, 2, android.graphics.Bitmap.Config.ARGB_8888)
        val bytes = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)
        bitmap.recycle()
        assertNull(com.dailybeat.app.ui.components.decodeMapTile(bytes.toByteArray()))
    }

    @Test fun failedStyleCanRetryWithoutDetachingTheNativeMap() {
        val light = File(directory, "light.json")
        val validStyle = light.readText()
        light.writeText("not a valid style")
        val visible = mutableStateOf(true)
        val journey = JourneyMapModel.fromPoints(listOf(
            JourneyPoint(60_000, 11.4557, 78.1856, "transit"),
            JourneyPoint(120_000, 11.46, 78.19, "transit"),
        ))
        val lease = LocalMapLease(app.offlineMaps.catalog, directory) {}
        compose.setContent {
            DailyBeatTheme(darkTheme = false) {
                if (visible.value) JourneyMapPreviewContent(journey, Modifier.fillMaxSize(), {},
                    MapPreferences(allowOnlineMaps = false), lease)
            }
        }
        try {
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("journey_map_retry").fetchSemanticsNodes().isNotEmpty()
            }
            // Removing and reattaching the same SurfaceView resets its native renderer.
            compose.onNodeWithTag("journey_map").assertExists()
            light.writeText(validStyle)
            compose.onNodeWithTag("journey_map_retry").performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag("journey_map_ready").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            compose.runOnUiThread { visible.value = false }
            compose.waitForIdle()
        }
    }

    @Test fun repeatedOpenCloseAndBackgroundWithFullPackageStaysHealthy() {
        org.junit.Assume.assumeNotNull(InstrumentationRegistry.getArguments().getString("fullMapDirectory"))
        val visible = mutableStateOf(true)
        val instance = mutableStateOf(0)
        val journey = JourneyMapModel.fromPoints(listOf(
            JourneyPoint(60_000, 11.4557, 78.1856, "transit"),
            JourneyPoint(120_000, 11.46, 78.19, "transit"),
        ))
        compose.setContent {
            DailyBeatTheme(darkTheme = instance.value % 2 == 0) {
                // Match MainActivity's theme surface/content color, not the test Activity's
                // unthemed window underneath the preview's translucent surface.
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    if (visible.value) key(instance.value) {
                        JourneyMapPreviewContent(journey, Modifier.fillMaxSize(), {},
                            MapPreferences(allowOnlineMaps = true), LocalMapLease(app.offlineMaps.catalog, directory) {})
                    }
                }
            }
        }
        try {
            repeat(12) { index ->
                compose.waitUntil(20_000) {
                    compose.onAllNodesWithTag("journey_map_ready").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(app.getString(R.string.map_using_offline)).assertExists()
                if (index == 0 || index == 11) savePhoneEvidence("interactive-texture-$index")
                compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
                compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
                compose.onNodeWithTag("journey_map").assertExists()
                compose.runOnUiThread { visible.value = false }
                compose.waitForIdle()
                System.gc()
                System.runFinalization()
                if (index < 11) compose.runOnUiThread { instance.value++; visible.value = true }
            }
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnUiThread { visible.value = false }
            compose.waitForIdle()
        }
    }

    private fun savePhoneEvidence(name: String) {
        if (InstrumentationRegistry.getArguments().getString("screenshots") != "true") return
        Thread.sleep(400) // Native label fade-in is independent of Compose idleness.
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val output = File(app.filesDir, "map-evidence").apply { mkdirs() }
        File(output, "$name.png").outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
    /** Optional full-package gate: pass fullMapDirectory containing the published artifacts. */
    @Test fun verifiedFullPackageActivatesAndCanBeDeletedWhileTheJourneyMapIsOpen() {
        val full = InstrumentationRegistry.getArguments().getString("fullMapDirectory")
        org.junit.Assume.assumeNotNull(full)
        runBlocking { app.offlineMaps.delete() }
        val staging = File(app.noBackupFilesDir, "offline-maps/staging/${app.offlineMaps.catalog.version}").apply { mkdirs() }
        File(full!!, "tamil-nadu.pmtiles").copyTo(File(staging, "tamil-nadu.pmtiles"), overwrite = true)
        File(full, "resources.zip").copyTo(File(staging, "assets.zip"), overwrite = true)
        runBlocking { app.offlineMaps.install { _, _ -> } }
        assertEquals(app.offlineMaps.catalog, app.offlineMaps.state.value.installed)
        val model = com.dailybeat.app.ui.components.JourneyMapModel.fromPoints(listOf(
            com.dailybeat.app.ui.components.JourneyPoint(0, 11.4557, 78.1856, "stay"),
            com.dailybeat.app.ui.components.JourneyPoint(60_000, 11.46, 78.19, "stay"),
        ))
        compose.setContent {
            com.dailybeat.app.ui.theme.DailyBeatTheme {
                com.dailybeat.app.ui.components.JourneyMapPreview(model, Modifier.fillMaxSize())
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("journey_map_ready", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(app.getString(R.string.map_using_offline)).assertExists()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val deleting = scope.async { app.offlineMaps.delete() }
            compose.waitUntil(15_000) { deleting.isCompleted }
            runBlocking { deleting.await() }
            assertNull(app.offlineMaps.state.value.installed)
            assertFalse(File(app.noBackupFilesDir, "offline-maps").exists())
            compose.onNodeWithText(app.getString(R.string.map_local_route_only)).assertExists()
        } finally { scope.cancel() }
    }

    @Test fun streetMapsRenderAcrossTamilNaduWithoutAnyHttpRequests() {
        val ready = CountDownLatch(1)
        var map: MapLibreMap? = null
        val errors = java.util.Collections.synchronizedList(mutableListOf<String>())
        compose.setContent {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                MapLibre.getInstance(context)
                app.mapNetwork.installNativeClient()
                HttpRequestUtil.setOkHttpClient { request ->
                    httpRequests.incrementAndGet()
                    app.mapNetwork.resources.newCall(request)
                }
                MapView(context).apply {
                    view = this
                    onCreate(Bundle()); onStart(); onResume()
                    addOnDidFailLoadingMapListener { errors.add(it) }
                    getMapAsync {
                        map = it
                        it.cameraPosition = CameraPosition.Builder().target(LatLng(13.0827, 80.2707)).zoom(14.5).build()
                        val style = prepareOfflineMapStyle(File(directory, "light.json").readText(), dark = false)
                            .replace("__PACK_ROOT__", Uri.fromFile(directory).toString())
                        it.setStyle(Style.Builder().fromJson(style)) { ready.countDown() }
                    }
                }
            })
        }
        assertTrue("Offline style failed: $errors", ready.await(15, TimeUnit.SECONDS))
        val points = listOf(
            "Chennai" to LatLng(13.0827, 80.2707), "Rasipuram" to LatLng(11.4557, 78.1856),
            "Namakkal" to LatLng(11.2194, 78.1674), "Coimbatore" to LatLng(11.0168, 76.9558),
            "Madurai" to LatLng(9.9252, 78.1198), "Kanyakumari" to LatLng(8.0883, 77.5385),
            "Hosur" to LatLng(12.7409, 77.8253), "Nagapattinam" to LatLng(10.7672, 79.8449),
            "Rameswaram" to LatLng(9.2883, 79.3129),
        )
        for (theme in listOf("light", "dark")) {
          val styled = CountDownLatch(1)
          compose.runOnUiThread {
            map!!.setStyle(Style.Builder().fromJson(prepareOfflineMapStyle(File(directory, "$theme.json").readText(), dark = theme == "dark")
                .replace("__PACK_ROOT__", Uri.fromFile(directory).toString()))) { styled.countDown() }
          }
          assertTrue(styled.await(15, TimeUnit.SECONDS))
          for ((name, point) in points) {
            compose.runOnUiThread { map!!.cameraPosition = CameraPosition.Builder().target(point).zoom(14.5).build() }
            var roads = 0
            var labels = 0
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline && (roads == 0 || labels == 0)) {
                Thread.sleep(150)
                compose.runOnUiThread {
                    val rect = RectF(0f, 0f, view!!.width.toFloat(), view!!.height.toFloat())
                    roads = map!!.queryRenderedFeatures(rect, "roads_minor", "roads_major", "roads_highway", "roads_minor_service").size
                    labels = map!!.queryRenderedFeatures(rect, "roads_labels_minor", "roads_labels_major", "pois", "places_locality", "places_subplace").size
                }
            }
            assertTrue("No roads rendered in $name ($theme); errors=$errors", roads > 0)
            assertTrue("No labels rendered in $name ($theme); errors=$errors", labels > 0)
            if (InstrumentationRegistry.getArguments().getString("screenshots") == "true") {
                Thread.sleep(400) // Allow label fade-in to finish before collecting visual evidence.
                val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val output = File(app.filesDir, "map-evidence").apply { mkdirs() }
                File(output, "$name-$theme.png").outputStream().use {
                    screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
            }
          }
        }
        assertTrue("Map errors: $errors", errors.isEmpty())
        assertEquals("Offline styles must not request tiles, sprites or fonts over HTTP", 0, httpRequests.get())
    }
}
