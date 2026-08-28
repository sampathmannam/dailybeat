package com.dailybeat.app.geo

import com.dailybeat.app.data.db.GeocodeDao
import com.dailybeat.app.data.model.GeocodeCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OpenStreetMap Nominatim reverse geocoding (free). Respects 1 req/s policy via mutex delay.
 */
class OsmGeocoder(
    private val geocodeDao: GeocodeDao,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val throttle = Mutex()
    private var lastRequestMs = 0L

    suspend fun resolve(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        val key = cacheKey(latitude, longitude)
        val cached = geocodeDao.get(key)
        if (cached != null) return@withContext cached.displayName

        throttle.withLock {
            val wait = 1100L - (System.currentTimeMillis() - lastRequestMs)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastRequestMs = System.currentTimeMillis()
        }

        val url = String.format(
            Locale.US,
            "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json&addressdetails=1",
            latitude,
            longitude,
        )
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DailyBeat/3.0 (offline diary; contact: dailybeat-app)")
            .header("Accept-Language", "en")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext fallbackLabel(latitude, longitude)
        }

        val body = response.body?.string() ?: return@withContext fallbackLabel(latitude, longitude)
        val json = JSONObject(body)
        val display = json.optString("display_name").takeIf { it.isNotBlank() }
            ?: fallbackLabel(latitude, longitude)

        geocodeDao.put(GeocodeCache(key = key, displayName = display))
        display
    }

    private fun cacheKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f,%.4f", lat, lon)

    private fun fallbackLabel(lat: Double, lon: Double): String =
        String.format(Locale.US, "Location %.4f, %.4f", lat, lon)
}
