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

/** What the map knows about a spot: its own name for it, plus the full postal address. */
data class ResolvedPlace(
    val name: String?,
    val address: String,
) {
    /** Best single label to show the officer, e.g. "Rasipuram Police Station". */
    val label: String get() = name ?: address.substringBefore(",").trim().ifBlank { address }
}

/**
 * OpenStreetMap Nominatim reverse geocoding (free). Respects 1 req/s policy via mutex delay.
 */
open class OsmGeocoder(
    private val geocodeDao: GeocodeDao,
    private val baseUrl: String = NOMINATIM_URL,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val throttle = Mutex()
    private var lastRequestMs = 0L

    open suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace =
        withContext(Dispatchers.IO) {
            if (!isValidCoordinate(latitude, longitude)) {
                return@withContext ResolvedPlace(null, fallbackLabel(latitude, longitude))
            }
            val key = cacheKey(latitude, longitude)
            runCatching { geocodeDao.get(key) }.getOrNull()?.let { cached ->
                return@withContext ResolvedPlace(cached.placeName, cached.displayName)
            }

            throttle.withLock {
                val wait = 1100L - (System.currentTimeMillis() - lastRequestMs)
                if (wait > 0) kotlinx.coroutines.delay(wait)
                lastRequestMs = System.currentTimeMillis()
            }

            // zoom=18 asks for building/POI granularity so named places such as a police
            // station come back as a name instead of just the street they sit on.
            val url = String.format(
                Locale.US,
                "%s?lat=%.6f&lon=%.6f&format=json&addressdetails=1&namedetails=1&zoom=18",
                baseUrl,
                latitude,
                longitude,
            )
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "DailyBeat (+https://github.com/sampathmannam/dailybeat)")
                .header("Accept-Language", "en")
                .build()

            val fallback = ResolvedPlace(null, fallbackLabel(latitude, longitude))
            val body = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext fallback
                    val responseBody = response.body ?: return@withContext fallback
                    if (responseBody.contentLength() > MAX_RESPONSE_BYTES) {
                        return@withContext fallback
                    }
                    val source = responseBody.source()
                    if (source.request(MAX_RESPONSE_BYTES + 1L)) return@withContext fallback
                    source.readUtf8()
                }
            } catch (_: Exception) {
                return@withContext fallback
            }

            val resolved = try {
                parse(JSONObject(body), latitude, longitude)
            } catch (_: Exception) {
                return@withContext fallback
            }

            runCatching {
                geocodeDao.put(
                    GeocodeCache(key = key, displayName = resolved.address, placeName = resolved.name),
                )
            }
            resolved
        }

    internal fun parse(json: JSONObject, latitude: Double, longitude: Double): ResolvedPlace {
        val address = json.optString("display_name").trimOrNull()?.take(MAX_ADDRESS_CHARS)
            ?: fallbackLabel(latitude, longitude)
        return ResolvedPlace(name = extractName(json), address = address)
    }

    /**
     * Nominatim reports a named feature in several places depending on its version and what
     * kind of feature it is, so take the first that actually names the spot rather than the
     * street or the town it sits in.
     */
    private fun extractName(json: JSONObject): String? {
        json.optJSONObject("namedetails")?.let { names ->
            names.optString("name").trimOrNull()?.let { return it.take(MAX_NAME_CHARS) }
            names.optString("name:en").trimOrNull()?.let { return it.take(MAX_NAME_CHARS) }
        }
        json.optString("name").trimOrNull()?.let { return it.take(MAX_NAME_CHARS) }
        json.optJSONObject("address")?.let { address ->
            NAMED_FEATURE_KEYS.forEach { key ->
                address.optString(key).trimOrNull()?.let { return it.take(MAX_NAME_CHARS) }
            }
        }
        return null
    }

    private fun String.trimOrNull(): String? = trim().takeIf { it.isNotEmpty() && it != "null" }

    private fun cacheKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f,%.4f", lat, lon)

    private fun fallbackLabel(lat: Double, lon: Double): String =
        String.format(Locale.US, "Location %.4f, %.4f", lat, lon)

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    private companion object {
        const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse"
        const val MAX_NAME_CHARS = 80
        const val MAX_ADDRESS_CHARS = 1_000
        const val MAX_RESPONSE_BYTES = 1L * 1024L * 1024L

        /**
         * Address keys that name a place rather than locate it, most specific first. Ordered so
         * a police station wins over the road it is on and the town it is in.
         */
        val NAMED_FEATURE_KEYS = listOf(
            "police",
            "amenity",
            "office",
            "building",
            "shop",
            "tourism",
            "historic",
            "leisure",
            "military",
            "healthcare",
            "hospital",
            "school",
            "college",
            "university",
            "place_of_worship",
            "neighbourhood",
            "hamlet",
            "suburb",
            "village",
            "town",
            "city",
        )
    }
}
