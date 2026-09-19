package com.dailybeat.app.maps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineMapRepositoryTest {
    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var root: File
    private val tiles = "trusted test tile archive".toByteArray()
    private val style = """{"version":8,"glyphs":"__PACK_ROOT__/fonts/{fontstack}/{range}.pbf","sprite":"__PACK_ROOT__/sprites/light","sources":{"map":{"type":"vector","url":"pmtiles://__PACK_ROOT__/tamil-nadu.pmtiles"}},"layers":[]}"""
    private val resources = mapOf("light.json" to style, "dark.json" to style,
        "region.geojson" to """{"type":"Polygon","coordinates":[[[76,8],[81,8],[81,14],[76,14],[76,8]]]}""")
    private val zip: ByteArray get() = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { output -> resources.forEach { (name, text) ->
            output.putNextEntry(ZipEntry(name)); output.write(text.toByteArray()); output.closeEntry()
        } }
    }.toByteArray()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun manifest(version: String): String {
        fun part(name: String, bytes: ByteArray) = JSONObject().put("name", name)
            .put("url", "https://github.com/sampathmannam/dailybeat/releases/download/v4.3.0/$name")
            .put("bytes", bytes.size).put("sha256", hash(bytes))
        return JSONObject().put("schema", 1).put("region", "tamil-nadu").put("version", version)
            .put("dataDate", "2026-09-19").put("bounds", JSONArray(listOf(76, 8, 81, 14)))
            .put("tiles", JSONArray().put(part("tiles", tiles))).put("tileBytes", tiles.size).put("tileSha256", hash(tiles))
            .put("assets", part("assets", zip)).put("unpackedAssetBytes", resources.values.sumOf { it.toByteArray().size }).toString()
    }
    private fun repository(version: String = "test-v1", space: Long = Long.MAX_VALUE) =
        OfflineMapRepository(context, { manifest(version) }, observeWork = false, availableSpace = { space }, client = client)
    private fun responses() { server.enqueue(MockResponse().setBody(Buffer().write(tiles))); server.enqueue(MockResponse().setBody(Buffer().write(zip))) }

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        root = File(context.noBackupFilesDir, "offline-maps").apply { deleteRecursively() }
        context.getSharedPreferences("dailybeat_map_download", Context.MODE_PRIVATE).edit().clear().commit()
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
    }
    @After fun cleanup() { client.dispatcher.cancelAll(); server.shutdown(); root.deleteRecursively() }

    @Test fun `low storage fails visibly before any transfer`() = runBlocking {
        val repository = repository(space = 1)
        try { repository.install { _, _ -> }; fail() } catch (_: IllegalArgumentException) { }
        assertEquals(MapDownloadStatus.FAILED, repository.state.value.status)
        assertTrue(repository.state.value.message!!.contains("storage"))
        assertEquals(0, server.requestCount)
    }
    @Test fun `partial bytes are discovered after process recreation and resumed`() = runBlocking {
        val partial = File(root, "staging/test-v1/tamil-nadu.pmtiles").apply { parentFile!!.mkdirs(); writeBytes(tiles.copyOfRange(0, 7)) }
        val repository = repository()
        assertEquals(MapDownloadStatus.PAUSED, repository.state.value.status)
        assertEquals(7, repository.state.value.downloadedBytes)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 7-${tiles.size - 1}/${tiles.size}")
            .setBody(Buffer().write(tiles.copyOfRange(7, tiles.size))))
        server.enqueue(MockResponse().setBody(Buffer().write(zip)))
        repository.install { _, _ -> }
        assertEquals("test-v1", repository.state.value.installed!!.version)
        assertFalse(partial.exists())
        assertEquals("bytes=7-", server.takeRequest().getHeader("Range"))
        assertEquals("test-v1", repository().state.value.installed!!.version)
    }
    @Test fun `checksum failure leaves previous package active`() = runBlocking {
        responses(); repository().install { _, _ -> }
        val update = repository("test-v2")
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(tiles.size))))
        try { update.install { _, _ -> }; fail() } catch (_: java.io.IOException) { }
        assertEquals("test-v1", update.state.value.installed!!.version)
        assertEquals("test-v1", repository("test-v2").state.value.installed!!.version)
        assertTrue(File(root, "versions/test-v1/tamil-nadu.pmtiles").exists())
    }
    @Test fun `uncommitted directory from interrupted activation is replaced by verified files`() = runBlocking {
        File(root, "versions/test-v1/tamil-nadu.pmtiles").apply { parentFile!!.mkdirs(); writeText("bad") }
        responses(); repository().install { _, _ -> }
        assertArrayEquals(tiles, File(root, "versions/test-v1/tamil-nadu.pmtiles").readBytes())
    }
    @Test fun `failed activation keeps active marker and previous map`() = runBlocking {
        responses(); repository().install { _, _ -> }
        val update = repository("test-v2")
        // AtomicFile cannot open this non-empty staging path, so commit must fail.
        File(root, "active.json.new").apply { mkdirs(); File(this, "blocker").writeText("x") }
        responses()
        try { update.install { _, _ -> }; fail() } catch (_: Exception) { }
        assertEquals("test-v1", update.state.value.installed!!.version)
        assertTrue(File(root, "versions/test-v1/tamil-nadu.pmtiles").exists())
    }
    @Test fun `deletion hides package then waits for renderer lease before removing files`() = runBlocking {
        val repository = repository()
        responses(); repository.install { _, _ -> }
        val lease = requireNotNull(repository.acquire())
        val removing = async(Dispatchers.IO) { repository.delete() }
        withTimeout(2000) { while (repository.state.value.status != MapDownloadStatus.DELETING) delay(10) }
        assertNull(repository.acquire())
        assertTrue(File(lease.directory, "tamil-nadu.pmtiles").isFile)
        assertFalse(removing.isCompleted)
        lease.close()
        withTimeout(2000) { removing.await() }
        assertFalse(root.exists())
        assertNull(repository().state.value.installed)
    }
    @Test fun `cancel waits for writer to close and preserves resumable bytes`() = runBlocking {
        val data = ByteArray(65536) { 42 }
        val file = File(context.cacheDir, "cancel-map-test").apply { delete() }
        server.enqueue(MockResponse().setBody(Buffer().write(data)).throttleBody(1024, 100, TimeUnit.MILLISECONDS))
        val task = launch(Dispatchers.IO) {
            MapPackInstaller(client).download(listOf(MapPart("tiles", server.url("/tiles").toString(), data.size.toLong(), hash(data))), file) { }
        }
        withTimeout(3000) { while (file.length() == 0L) delay(10) }
        withTimeout(2000) { task.cancelAndJoin() }
        val saved = file.length()
        delay(200)
        assertEquals(saved, file.length())
        assertTrue(saved in 1 until data.size)
        file.delete()
        Unit
    }
}
