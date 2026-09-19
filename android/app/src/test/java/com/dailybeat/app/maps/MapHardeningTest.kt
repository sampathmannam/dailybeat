package com.dailybeat.app.maps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapHardeningTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var directory: File
    private val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("dailybeat_maps", Context.MODE_PRIVATE).edit().clear().commit()
        directory = File(context.cacheDir, "map-regression-tests").apply { deleteRecursively(); mkdirs() }
        server = MockWebServer().apply { start() }
    }
    @After fun cleanup() { server.shutdown(); client.dispatcher.cancelAll(); directory.deleteRecursively() }
    private fun bytesHash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun part(bytes: ByteArray, path: String = "/map") = MapPart("part.bin", server.url(path).toString(), bytes.size.toLong(), bytesHash(bytes))
    private suspend fun read(limit: Long = 1024) = client.newCall(Request.Builder().url(server.url("/tile")).build()).awaitBoundedBytes(limit)

    @Test fun `fixed and chunked oversized responses are bounded`() = runBlocking {
        for (response in listOf(MockResponse().setBody("x".repeat(4096)), MockResponse().setChunkedBody("x".repeat(4096), 127))) {
            server.enqueue(response)
            try { read(); fail("oversized response accepted") } catch (_: IOException) { }
        }
    }
    @Test fun `transparent gzip expansion is bounded`() = runBlocking {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(ByteArray(100_000) { 65 }) }
        assertTrue(output.size() < 1024)
        server.enqueue(MockResponse().setHeader("Content-Encoding", "gzip").setBody(Buffer().write(output.toByteArray())))
        try { read(); fail("expanded response accepted") } catch (_: IOException) { }
    }
    @Test fun `exact boundary response succeeds`() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(1024), 127))
        assertEquals(1024, read().size)
    }
    @Test fun `cancelling a stalled request closes it promptly`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val pending = async(Dispatchers.IO) { read() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        withTimeout(1000) { pending.cancel(); pending.join() }
    }
    @Test fun `disabled online maps reject before reaching a server and survive restart`() = runBlocking {
        val settings = MapSettingsRepository(context)
        assertTrue(settings.state.value.allowOnlineMaps)
        settings.setOnline(false)
        val network = MapNetwork(context, MapSettingsRepository(context))
        try { network.resources.newCall(Request.Builder().url(server.url("/style")).build()).awaitBoundedBytes(1024); fail() }
        catch (error: IOException) { assertEquals("Online maps are disabled", error.message) }
        assertEquals(0, server.requestCount)
        network.clearCache()
    }
    @Test fun `provider validation rejects unsafe URLs and missing placeholders`() {
        for (provider in listOf(MapProviderConfig(styleUrl = "http://maps.example/style"),
            MapProviderConfig(styleUrl = "https://user:password@maps.example/style"),
            MapProviderConfig(styleUrl = "https://maps.example/style?token=secret"),
            MapProviderConfig(rasterTileTemplate = "https://maps.example/{z}/{x}.png"))) {
            assertThrows(IllegalArgumentException::class.java) { provider.validated() }
        }
        MapProviderConfig().validated()
    }
    @Test fun `provider and map choice persist independently of diary settings`() {
        val repository = MapSettingsRepository(context)
        repository.setProvider(MapProviderConfig(styleUrl = "https://maps.example/style.json"))
        repository.setOnline(false)
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(repository.state.value, MapSettingsRepository(context).state.value)
    }
    @Test fun `download resumes a partially written part`() = runBlocking {
        val bytes = "0123456789".toByteArray()
        val destination = File(directory, "map.pmtiles").apply { writeText("0123") }
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-9/10").setBody("456789"))
        MapPackInstaller(client).download(listOf(part(bytes)), destination) { }
        assertEquals("bytes=4-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, destination.readBytes())
    }
    @Test fun `server ignoring range restarts the current part without duplication`() = runBlocking {
        val bytes = "0123456789".toByteArray()
        val destination = File(directory, "map.pmtiles").apply { writeText("0123") }
        server.enqueue(MockResponse().setBody("0123456789"))
        MapPackInstaller(client).download(listOf(part(bytes)), destination) { }
        assertArrayEquals(bytes, destination.readBytes())
    }
    @Test fun `damaged part is discarded but previous verified part is retained`() = runBlocking {
        val first = part("first".toByteArray(), "/first")
        val second = part("second".toByteArray(), "/second")
        val destination = File(directory, "map.pmtiles").apply { writeText("first") }
        server.enqueue(MockResponse().setBody("broken"))
        try { MapPackInstaller(client).download(listOf(first, second), destination) { }; fail() } catch (_: IOException) { }
        assertEquals("first", destination.readText())
        assertEquals("/second", server.takeRequest().path)
    }
    @Test fun `pack extraction rejects traversal and excessive expansion`() = runBlocking {
        suspend fun check(name: String, max: Long) {
            val zip = File(directory, "assets.zip")
            ZipOutputStream(zip.outputStream()).use { it.putNextEntry(ZipEntry(name)); it.write(ByteArray(2048)); it.closeEntry() }
            try { MapPackInstaller(client).unpack(zip, File(directory, "resources"), max); fail() }
            catch (_: IllegalArgumentException) { }
        }
        check("../outside", 2048)
        check("../resources-evil/outside", 2048)
        check("/absolute/outside", 2048)
        check("./tamil-nadu.pmtiles", 2048)
        assertFalse(File(directory, "outside").exists())
        check("fonts/font.pbf", 1024)
    }
    @Test fun `coverage uses polygon holes instead of just bounding boxes`() {
        val coverage = MapCoverage(JSONObject("""{"type":"Polygon","coordinates":[[[0,0],[10,0],[10,10],[0,10],[0,0]],[[4,4],[6,4],[6,6],[4,6],[4,4]]]}"""))
        assertTrue(coverage.contains(2.0, 2.0))
        assertFalse(coverage.contains(5.0, 5.0))
        assertFalse(coverage.contains(20.0, 5.0))
    }
    @Test fun `native resources and raster requests all fail closed when disabled`() = runBlocking {
        val settings = MapSettingsRepository(context).apply { setOnline(false) }
        val network = MapNetwork(context, settings)
        for (client in listOf(network.resources, network.tiles)) {
            for (path in listOf("style.json", "sprite.png", "font.pbf", "tile.pbf")) {
                try { client.newCall(Request.Builder().url(server.url("/$path")).build()).awaitBoundedBytes(1024); fail() }
                catch (_: IOException) { }
            }
        }
        assertEquals(0, server.requestCount)
    }
    @Test fun `whole operation deadline cancels a stalled provider`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try { withTimeout(200) { read() }; fail() } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    }
    @Test fun `offline style rejects every external resource type`() {
        val template = """{"glyphs":"__PACK_ROOT__/fonts/{fontstack}/{range}.pbf","sprite":"__PACK_ROOT__/sprite","sources":{"map":{"type":"vector","url":"pmtiles://__PACK_ROOT__/tamil-nadu.pmtiles","attribution":"https://www.openstreetmap.org/copyright"}}}"""
        validateOfflineStyle(template)
        for (path in listOf("__PACK_ROOT__/fonts/{fontstack}/{range}.pbf", "__PACK_ROOT__/sprite", "pmtiles://__PACK_ROOT__/tamil-nadu.pmtiles")) {
            assertThrows(IllegalArgumentException::class.java) { validateOfflineStyle(template.replace(path, "https://unexpected.example/resource")) }
        }
    }

    @Test fun `changing map network settings cancels active native and thumbnail requests`() = runBlocking {
        val settings = MapSettingsRepository(context)
        val network = MapNetwork(context, settings)
        val calls = listOf(network.tiles, network.resources).map { client ->
            client.newCall(Request.Builder().url(server.url("/pending").newBuilder().scheme("https").build()).build())
        }
        val jobs = calls.map { call -> launch(Dispatchers.IO) { try { call.awaitBoundedBytes(1024) } catch (_: IOException) { } } }
        withTimeout(2000) {
            while (network.tiles.dispatcher.runningCallsCount() == 0 || network.resources.dispatcher.runningCallsCount() == 0) delay(10)
        }
        settings.setOnline(false)
        assertTrue(calls.all { it.isCanceled() })
        withTimeout(2000) { jobs.forEach { it.join() } }
        network.clearCache()
    }

}
