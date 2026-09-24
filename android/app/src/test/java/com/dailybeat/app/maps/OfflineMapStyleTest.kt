package com.dailybeat.app.maps

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineMapStyleTest {
    @get:Rule val temporary = TemporaryFolder()

    private val archive: File
        get() = listOf(
            File("src/androidTest/assets/offline-map/resources.zip"),
            File("app/src/androidTest/assets/offline-map/resources.zip"),
            File("android/app/src/androidTest/assets/offline-map/resources.zip"),
        ).firstOrNull { it.isFile } ?: error("Published offline map test resource is missing")

    private fun style(theme: String): String = ZipFile(archive).use { zip ->
        zip.getInputStream(requireNotNull(zip.getEntry("$theme.json"))).bufferedReader().use { it.readText() }
    }

    private fun layers(style: JSONObject): List<JSONObject> = style.getJSONArray("layers").let { rows ->
        (0 until rows.length()).map(rows::getJSONObject)
    }

    private fun isLabel(layer: JSONObject) = layer.optString("type") == "symbol" &&
        layer.optJSONObject("layout")?.has("text-field") == true

    private fun isRoad(layer: JSONObject) = layer.optString("type") == "line" &&
        layer.optString("id").startsWith("roads_") && !layer.optString("id").contains("_casing")

    @Test fun lightStyleRemainsByteForByteUnchanged() {
        val original = style("light")
        assertEquals(original, prepareOfflineMapStyle(original, dark = false))
    }

    @Test fun darkLabelsHaveAaContrastAgainstTheirHalosAndAuthoredMapSurfaces() {
        val original = JSONObject(style("dark"))
        val display = JSONObject(prepareOfflineMapStyle(original.toString(), dark = true))
        val surfaces = surfaceColors(original)
        assertTrue(surfaces.size > 10) // Real land, water and background paints, not one mock color.
        val labels = layers(display).filter(::isLabel)
        assertTrue(labels.size >= 10)
        labels.forEach { layer ->
            val paint = layer.getJSONObject("paint")
            val ink = requireNotNull(rgb(paint.getString("text-color")))
            val halo = requireNotNull(rgb(paint.getString("text-halo-color")))
            val id = layer.getString("id")
            assertTrue("$id halo contrast", contrast(ink, halo) >= 4.5)
            surfaces.forEach { assertTrue("$id surface contrast", contrast(ink, it) >= 4.5) }
            assertTrue("$id dark halo must separate labels from roads and shields", paint.getDouble("text-halo-width") >= 1)
            assertEquals(1.0, paint.getDouble("text-opacity"), 0.0)
        }
    }

    @Test fun essentialRoadsKeepAReadableNeutralHierarchyAgainstTheActualDarkSurfaces() {
        val original = JSONObject(style("dark"))
        val display = JSONObject(prepareOfflineMapStyle(original.toString(), dark = true))
        val surfaces = surfaceColors(original)
        val roads = layers(display).filter(::isRoad)
        assertTrue(roads.size >= 15)
        roads.forEach { layer ->
            val paint = layer.getJSONObject("paint")
            val color = requireNotNull(rgb(paint.getString("line-color")))
            assertEquals(color[0], color[1], 0.0)
            assertEquals(color[1], color[2], 0.0)
            surfaces.forEach { assertTrue("${layer.getString("id")} street contrast", contrast(color, it) >= 3.0) }
            assertEquals(1.0, paint.getDouble("line-opacity"), 0.0)
        }
        val colors = roads.associate { it.getString("id") to luminance(requireNotNull(rgb(it.getJSONObject("paint").getString("line-color")))) }
        assertTrue(colors.getValue("roads_minor") < colors.getValue("roads_major"))
        assertTrue(colors.getValue("roads_major") < colors.getValue("roads_highway"))
    }

    @Test fun normalizationPreservesAllResourcesGeometryLayoutAndUnrelatedPaint() {
        val original = JSONObject(style("dark"))
        val display = JSONObject(prepareOfflineMapStyle(original.toString(), dark = true))
        val before = layers(original)
        val after = layers(display)
        original.remove("layers")
        display.remove("layers")
        assertEquals(original.toString(), display.toString()) // URLs, attribution, sources, sprite and glyphs.
        assertEquals(before.size, after.size)
        before.zip(after).forEach { (source, target) ->
            val allowed = when {
                isLabel(source) -> setOf("text-color", "text-halo-color", "text-halo-width", "text-opacity")
                isRoad(source) -> setOf("line-color", "line-opacity")
                else -> emptySet()
            }
            val oldPaint = source.optJSONObject("paint")
            val newPaint = target.optJSONObject("paint")
            allowed.forEach { key -> oldPaint?.remove(key); newPaint?.remove(key) }
            assertEquals("Only approved paint may change in ${source.getString("id")}", source.toString(), target.toString())
        }
    }

    @Test fun publishedResourceHashIsUnchangedAndNormalizationIsIdempotent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = JSONObject(context.assets.open("offline/tamil-nadu.json").bufferedReader().use { it.readText() })
        val expectedHash = catalog.getJSONObject("assets").getString("sha256")
        assertEquals(expectedHash, hash(archive))
        val once = prepareOfflineMapStyle(style("dark"), dark = true)
        assertEquals(once, prepareOfflineMapStyle(once, dark = true))
        assertEquals(expectedHash, hash(archive))
    }

    @Test fun installedLeaseUsesTheSameNormalizerWithoutRewritingVerifiedFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catalog = context.assets.open("offline/tamil-nadu.json").bufferedReader().use { it.readText() }
        val directory = temporary.newFolder("verified-map-copy")
        val dark = File(directory, "dark.json").apply { writeText(style("dark")) }
        val light = File(directory, "light.json").apply { writeText(style("light")) }
        val originalDark = dark.readText()
        val originalLight = light.readText()
        val root = Uri.fromFile(directory).toString().trimEnd('/')
        val lease = LocalMapLease(OfflineMapManifest.parse(catalog), directory) {}
        lease.use {
            assertEquals(prepareOfflineMapStyle(originalDark, dark = true).replace("__PACK_ROOT__", root), it.style(dark = true))
            assertEquals(originalLight.replace("__PACK_ROOT__", root), it.style(dark = false))
        }
        assertEquals(originalDark, dark.readText())
        assertEquals(originalLight, light.readText())
    }

    private fun surfaceColors(style: JSONObject): List<DoubleArray> = layers(style)
        .filter { it.optString("type") in setOf("fill", "background") }
        .flatMap { layer ->
            val paint = layer.optJSONObject("paint") ?: return@flatMap emptyList()
            colors(paint.opt(if (layer.optString("type") == "background") "background-color" else "fill-color"))
        }

    private fun colors(value: Any?): List<DoubleArray> = when (value) {
        is String -> listOfNotNull(rgb(value))
        is JSONArray -> (0 until value.length()).flatMap { colors(value.opt(it)) }
        else -> emptyList()
    }

    private fun rgb(color: String): DoubleArray? {
        if (color.matches(Regex("#[0-9a-fA-F]{6}"))) {
            return doubleArrayOf(color.substring(1, 3).toInt(16).toDouble(), color.substring(3, 5).toInt(16).toDouble(), color.substring(5, 7).toInt(16).toDouble())
        }
        if (color.startsWith("rgba(")) {
            val channels = color.removePrefix("rgba(").removeSuffix(")").split(',').map { it.trim().toDouble() }
            require(channels.size == 4 && channels[3] == 1.0) { "Surface alpha needs explicit composition in the contrast test" }
            return channels.take(3).toDoubleArray()
        }
        return null
    }

    private fun luminance(rgb: DoubleArray): Double = rgb.map { component ->
        val value = component / 255.0
        if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }

    private fun contrast(one: DoubleArray, two: DoubleArray): Double {
        val a = luminance(one)
        val b = luminance(two)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
