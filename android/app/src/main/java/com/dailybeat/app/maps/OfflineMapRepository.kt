package com.dailybeat.app.maps

import android.content.Context
import android.util.AtomicFile
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class MapDownloadStatus { IDLE, WAITING, DOWNLOADING, VERIFYING, PAUSED, FAILED, DELETING }
data class OfflineMapState(
    val installed: OfflineMapManifest? = null,
    val status: MapDownloadStatus = MapDownloadStatus.IDLE,
    val downloadedBytes: Long = 0,
    val message: String? = null,
)

class LocalMapLease internal constructor(
    val manifest: OfflineMapManifest,
    val directory: File,
    private val release: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val region by lazy { MapCoverage(JSONObject(File(directory, "region.geojson").readText())) }
    fun contains(latitude: Double, longitude: Double) = manifest.contains(latitude, longitude) && region.contains(latitude, longitude)
    fun style(dark: Boolean): String = prepareOfflineMapStyle(
        File(directory, if (dark) "dark.json" else "light.json").readText(), dark,
    ).replace("__PACK_ROOT__", android.net.Uri.fromFile(directory).toString().trimEnd('/'))
    override fun close() { if (closed.compareAndSet(false, true)) release() }
}

class OfflineMapRepository internal constructor(
    private val context: Context,
    private val catalogLoader: () -> String = { context.assets.open("offline/tamil-nadu.json").bufferedReader().use { it.readText() } },
    private val observeWork: Boolean = true,
    private val availableSpace: (File) -> Long = { it.usableSpace },
    private val client: OkHttpClient = downloadClient(context),
) {
    private val root = File(context.noBackupFilesDir, "offline-maps")
    private val marker = AtomicFile(File(root, "active.json"))
    private val lock = Mutex()
    private val leases = mutableMapOf<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("dailybeat_map_download", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(initialState())
    val state = mutable.asStateFlow()
    val catalog: OfflineMapManifest by lazy { OfflineMapManifest.parse(catalogText()) }

    init {
        if (observeWork) scope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME).collect { work ->
                val active = work.firstOrNull { !it.state.isFinished }
                if (active?.state == WorkInfo.State.ENQUEUED && state.value.status != MapDownloadStatus.DELETING) {
                    mutable.value = state.value.copy(status = MapDownloadStatus.WAITING,
                        message = "Waiting for the selected network and available storage.")
                } else if (active == null && state.value.status == MapDownloadStatus.WAITING) {
                    mutable.value = state.value.copy(status = MapDownloadStatus.PAUSED)
                }
            }
        }
    }

    private fun catalogText() = catalogLoader()
    private fun initialState(): OfflineMapState {
        val partialBytes = File(root, "staging").walkTopDown().filter { it.isFile && it.name in setOf("tamil-nadu.pmtiles", "assets.zip") }.sumOf { it.length() }
        val interrupted = prefs.getBoolean("pending", false) || partialBytes > 0
        return OfflineMapState(installed = readInstalled(),
            status = if (interrupted) MapDownloadStatus.PAUSED else MapDownloadStatus.IDLE,
            downloadedBytes = partialBytes,
            message = if (interrupted) "Download saved. Resume when ready." else null)
    }
    private fun versionDir(version: String) = File(root, "versions/$version")
    private fun readInstalled(): OfflineMapManifest? = runCatching {
        val installed = marker.openRead().bufferedReader().use { OfflineMapManifest.parse(it.readText()) }
        require(File(versionDir(installed.version), "tamil-nadu.pmtiles").length() == installed.tileBytes)
        require(listOf("light.json", "dark.json", "region.geojson").all { File(versionDir(installed.version), it).isFile })
        installed
    }.getOrNull()

    fun acquire(): LocalMapLease? = synchronized(leases) {
        val installed = state.value.installed ?: return null
        leases[installed.version] = (leases[installed.version] ?: 0) + 1
        LocalMapLease(installed, versionDir(installed.version)) {
            synchronized(leases) { leases[installed.version] = (leases[installed.version] ?: 1) - 1 }
            scope.launch { lock.withLock { cleanOldVersions() } }
        }
    }

    fun start(allowMobileData: Boolean) {
        check(state.value.status != MapDownloadStatus.DELETING)
        check(prefs.edit().putBoolean("mobile", allowMobileData).putBoolean("pending", true).commit())
        mutable.value = state.value.copy(status = MapDownloadStatus.WAITING, message = null)
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MapDownloadWorker>().setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if (allowMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .setRequiresStorageNotLow(true).build()).build())
    }

    fun mobileDataAllowed() = prefs.getBoolean("mobile", false)

    suspend fun pause() = withContext(Dispatchers.IO) {
        if (observeWork) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
        client.dispatcher.cancelAll()
        lock.withLock { mutable.value = state.value.copy(status = MapDownloadStatus.PAUSED, message = "Download paused. Resume when ready.") }
    }

    internal fun failed(error: Exception) {
        mutable.update { it.copy(status = MapDownloadStatus.FAILED,
            message = error.message?.take(180) ?: "Map download failed. Try again.") }
    }

    internal suspend fun install(progress: suspend (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        lock.withLock {
          try {
            val manifest = catalog
            if (state.value.installed == manifest) {
                prefs.edit().putBoolean("pending", false).commit()
                mutable.value = OfflineMapState(installed = manifest)
                return@withLock
            }
            check(prefs.edit().putBoolean("pending", true).commit())
            File(root, "staging").listFiles().orEmpty().filter { it.name != manifest.version }.forEach {
                check(it.deleteRecursively()) { "Could not clean an older incomplete map download." }
            }
            val staging = File(root, "staging/${manifest.version}").apply { mkdirs() }
            val tiles = File(staging, "tamil-nadu.pmtiles")
            val assets = File(staging, "assets.zip")
            val remaining = (manifest.tileBytes - tiles.length()).coerceAtLeast(0) +
                (manifest.assets.bytes - assets.length()).coerceAtLeast(0) + manifest.unpackedAssetBytes
            require(availableSpace(staging) >= remaining + 100L * 1024 * 1024) {
                "Not enough storage for the map and a safe update. Free some space and resume."
            }
            mutable.value = state.value.copy(status = MapDownloadStatus.DOWNLOADING, message = null)
            val installer = MapPackInstaller(client)
            var lastProgress = 0L
            fun report(bytes: Long) {
                // Network callbacks must not flood Compose or notification/WorkManager writes.
                val now = System.nanoTime()
                if (now - lastProgress > 500_000_000 || bytes == manifest.downloadBytes) {
                    lastProgress = now
                    mutable.update { it.copy(downloadedBytes = bytes) }
                }
            }
            // A structured progress job cannot outlive installation or deletion.
            kotlinx.coroutines.coroutineScope {
              val notifications = launch {
                while (true) { progress(state.value.downloadedBytes, manifest.downloadBytes); delay(1000) }
              }
              try {
                installer.download(manifest.tiles, tiles, ::report)
                installer.download(listOf(manifest.assets), assets) { report(manifest.tileBytes + it) }
              } finally { notifications.cancel() }
            }
                mutable.value = state.value.copy(status = MapDownloadStatus.VERIFYING, message = "Checking downloaded map…")
                require(sha256(tiles) == manifest.tileSha256) { "Map verification failed. Delete the download and try again." }
                installer.unpack(assets, staging, manifest.unpackedAssetBytes)
                require(listOf("light.json", "dark.json", "region.geojson").all { File(staging, it).isFile })
                for (name in listOf("light.json", "dark.json")) {
                    validateOfflineStyle(File(staging, name).readText())
                }
                File(staging, "manifest.json").writeText(catalogText())
                assets.delete()
                val destination = versionDir(manifest.version)
                destination.parentFile!!.mkdirs()
                // A crash may leave an uncommitted directory. Never trust it merely because
                // its name matches; replace it with this fully verified staging directory.
                check(state.value.installed?.version != manifest.version) { "Remove the old map before reinstalling this version." }
                check(synchronized(leases) { (leases[manifest.version] ?: 0) == 0 }) { "Close the map and retry the update." }
                check(!destination.exists() || destination.deleteRecursively()) { "Could not clean an interrupted update." }
                check(staging.renameTo(destination)) { "Could not install the map." }
                val output = marker.startWrite()
                try { output.write(catalogText().toByteArray()); marker.finishWrite(output) }
                catch (error: Exception) { marker.failWrite(output); throw error }
                prefs.edit().putBoolean("pending", false).commit()
                mutable.value = OfflineMapState(installed = manifest, downloadedBytes = manifest.downloadBytes,
                    message = "Tamil Nadu is ready for offline use.")
                cleanOldVersions()
            } catch (cancelled: CancellationException) {
                mutable.value = state.value.copy(status = MapDownloadStatus.PAUSED)
                throw cancelled
            } catch (error: Exception) {
                failed(error)
                throw error
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        if (observeWork) WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME).result.get()
        client.dispatcher.cancelAll()
        lock.withLock {
            val previous = state.value.installed
            mutable.value = state.value.copy(installed = null, status = MapDownloadStatus.DELETING)
            try {
                withTimeout(10_000) {
                    while (synchronized(leases) { leases.values.any { it > 0 } }) delay(25)
                }
                check(!root.exists() || root.deleteRecursively()) { "Could not remove the offline map. Close the map and try again." }
                prefs.edit().clear().commit()
                mutable.value = OfflineMapState(message = "Offline map removed.")
            } catch (error: Exception) {
                mutable.value = OfflineMapState(installed = previous, status = MapDownloadStatus.FAILED,
                    message = "Close the map and try deleting it again.")
                throw error
            }
        }
    }

    private fun cleanOldVersions() {
        val active = state.value.installed?.version
        File(root, "versions").listFiles().orEmpty().filter {
            it.name != active && synchronized(leases) { (leases[it.name] ?: 0) == 0 }
        }.forEach { it.deleteRecursively() }
    }

    companion object {
        const val WORK_NAME = "dailybeat-tamil-nadu-map"
        private fun downloadClient(context: Context) = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (!chain.request().url.isHttps) throw IOException("Map downloads require HTTPS")
                val mobile = context.getSharedPreferences("dailybeat_map_download", Context.MODE_PRIVATE).getBoolean("mobile", false)
                val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
                val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                if (!mobile && capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
                    throw IOException("Waiting for Wi-Fi. Enable mobile data in map settings to use cellular downloads.")
                }
                chain.proceed(chain.request())
            }.build()
    }
}

internal fun validateOfflineStyle(text: String) {
    val style = JSONObject(text)
    require(!style.has("imports"))
    fun local(url: String) {
        require((url.startsWith("__PACK_ROOT__/") || url.startsWith("pmtiles://__PACK_ROOT__/")) &&
            !url.contains("..")) { "Offline style contains an online resource." }
    }
    local(style.getString("glyphs"))
    local(style.getString("sprite"))
    val sources = style.getJSONObject("sources")
    sources.keys().forEach { key ->
        val source = sources.getJSONObject(key)
        require(source.getString("type") == "vector")
        local(source.getString("url"))
        require(!source.has("tiles"))
    }
}

/** Boundary selection prevents treating neighbouring states inside a bounding box as covered. */
internal class MapCoverage(feature: JSONObject) {
    private val geometry = if (feature.has("geometry")) feature.getJSONObject("geometry") else feature
    private val coordinates = geometry.getJSONArray("coordinates")
    private val polygons = if (geometry.getString("type") == "Polygon") listOf(coordinates)
        else (0 until coordinates.length()).map { coordinates.getJSONArray(it) }

    fun contains(latitude: Double, longitude: Double): Boolean = polygons.any { polygon ->
        fun ringContains(index: Int): Boolean {
            val ring = polygon.getJSONArray(index)
            var inside = false
            var j = ring.length() - 1
            for (i in 0 until ring.length()) {
                val a = ring.getJSONArray(i); val b = ring.getJSONArray(j)
                val ax = a.getDouble(0); val ay = a.getDouble(1)
                val bx = b.getDouble(0); val by = b.getDouble(1)
                if ((ay > latitude) != (by > latitude) && longitude < (bx - ax) * (latitude - ay) / (by - ay) + ax) inside = !inside
                j = i
            }
            return inside
        }
        ringContains(0) && (1 until polygon.length()).none(::ringContains)
    }
}
