package com.dailybeat.app.maps

import android.content.Context
import com.dailybeat.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import org.maplibre.android.module.http.HttpRequestUtil
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Only map rendering uses these clients. Capture, geocoding and backup are independent. */
class MapNetwork(private val context: Context, private val settings: MapSettingsRepository) {
    private val cache = Cache(File(context.cacheDir, "osm-journey-map-tiles"), 64L * 1024 * 1024)
    private fun builder() = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (!settings.state.value.allowOnlineMaps) throw IOException("Online maps are disabled")
            chain.proceed(chain.request().newBuilder().header("User-Agent",
                "DailyBeat/${BuildConfig.VERSION_NAME} (+https://github.com/sampathmannam/dailybeat)").build())
        }
        .addNetworkInterceptor { chain ->
            if (!settings.state.value.allowOnlineMaps || !chain.request().url.isHttps) {
                throw IOException("Map network request is disabled")
            }
            chain.proceed(chain.request())
        }
        .followSslRedirects(false)

    val tiles: OkHttpClient = builder().cache(cache).build()
    val resources: OkHttpClient = builder().build()

    init { settings.onChange = ::cancelRequests }

    @androidx.annotation.MainThread
    fun installNativeClient() {
        // HttpRequestImpl initializes its identifier from MapLibre's application context.
        org.maplibre.android.MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(resources)
        HttpRequestUtil.setPrintRequestUrlOnFailure(false)
        HttpRequestUtil.setLogEnabled(false)
    }

    fun cancelRequests() {
        tiles.dispatcher.cancelAll()
        resources.dispatcher.cancelAll()
    }

    fun clearCache() { cancelRequests(); cache.evictAll() }

    suspend fun clearNativeCache() {
        withContext(Dispatchers.Main) {
            // Also clear a cache left by an earlier process, before any map opened this session.
            org.maplibre.android.MapLibre.getInstance(context)
            installNativeClient()
            withTimeout(10_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    org.maplibre.android.offline.OfflineManager.getInstance(context).resetDatabase(
                        object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                            override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                            override fun onError(message: String) {
                                if (continuation.isActive) continuation.resumeWithException(IOException("Could not clear map cache"))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Bounds decompressed bytes as well as declared lengths; never buffers an unbounded body. */
internal fun ResponseBody.readBounded(maxBytes: Long): ByteArray {
    if (contentLength() > maxBytes) throw IOException("Map response exceeds its size limit")
    val source = source()
    if (source.request(maxBytes + 1)) throw IOException("Map response exceeds its size limit")
    return source.readByteArray()
}

/** Body reads run on OkHttp's callback thread; cancellation closes the active socket/read. */
internal suspend fun Call.awaitBoundedBytes(maxBytes: Long): ByteArray = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(error)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val bytes = response.use {
                    if (!it.isSuccessful) throw IOException("Map provider is unavailable")
                    (it.body ?: throw IOException("Empty map response")).readBounded(maxBytes)
                }
                if (!continuation.isCancelled) continuation.resume(bytes)
            } catch (error: Exception) {
                if (!continuation.isCancelled) continuation.resumeWithException(error)
            }
        }
    })
}
