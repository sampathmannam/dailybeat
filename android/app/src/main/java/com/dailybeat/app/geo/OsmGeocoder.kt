package com.dailybeat.app.geo

import android.os.SystemClock
import com.dailybeat.app.data.db.GeocodeDao
import com.dailybeat.app.data.model.GeocodeCache
import com.dailybeat.app.util.InputPolicy
import com.dailybeat.app.util.isBoundedJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.coroutines.resume

/** A map-derived location hint, not evidence that the user entered a particular venue. */
data class ResolvedPlace(
    val name: String?,
    val address: String,
) {
    /** Automatic venue candidates keep their "Near" qualifier; saved names are applied separately. */
    val label: String get() = name ?: address.substringBefore(",").trim().ifBlank { address }
}

/**
 * Optional Nominatim-compatible managed endpoint. No public service is contacted by default.
 */
open class OsmGeocoder(
    private val geocodeDao: GeocodeDao,
    private val baseUrl: String = "",
    private val endpoint: () -> String = { baseUrl },
    private val permitsLookup: suspend (Double, Double) -> Boolean = { _, _ -> true },
    private val minimumRequestIntervalMs: Long = 1_100L,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val throttle = GeocoderRequestThrottle(minimumRequestIntervalMs, elapsedRealtimeMs)

    open suspend fun resolve(latitude: Double, longitude: Double): ResolvedPlace =
        withContext(Dispatchers.IO) {
            if (!isValidCoordinate(latitude, longitude)) {
                return@withContext ResolvedPlace(null, fallbackLabel())
            }
            val configuredEndpoint = endpoint()
            if (configuredEndpoint.isBlank() || !permitsLookup(latitude, longitude)) {
                return@withContext ResolvedPlace(null, "Unnamed place")
            }
            // Do not reuse older exact-name guesses made from POIs as far as 200 metres away.
            // This only retires derived lookup cache entries, never the user's named/history data.
            val key = configuredEndpoint + ":v3:" + cacheKey(latitude, longitude)
            val cachedPlace = try {
                geocodeDao.get(key)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            cachedPlace?.takeIf {
                val ageMs = System.currentTimeMillis() - it.fetchedAt
                ageMs in 0..MAX_CACHE_AGE_MS
            }?.let { cached ->
                if (endpoint() != configuredEndpoint || !permitsLookup(latitude, longitude)) {
                    return@withContext ResolvedPlace(null, fallbackLabel())
                }
                return@withContext ResolvedPlace(cached.placeName, cached.displayName)
            }

            val fallback = ResolvedPlace(null, fallbackLabel())
            // Nominatim returns the nearest suitable indexed object, not the occupied venue.
            // A nearby classified POI is only a candidate, even at zero centroid distance.
            val poiLookup = requestJson(configuredEndpoint, latitude, longitude, layer = "poi")
            val resolved = when (poiLookup) {
                is LookupResult.Found -> {
                    val nearbyPoi = parsePoi(poiLookup.json)
                        ?.takeIf { it.name != null && poiLookup.json.isNear(latitude, longitude) }
                    nearbyPoi ?: when (
                        val addressLookup = requestJson(
                            configuredEndpoint,
                            latitude,
                            longitude,
                            layer = "address",
                        )
                    ) {
                        is LookupResult.Found -> runCatching {
                            parse(addressLookup.json)
                        }.getOrNull() ?: fallback
                        LookupResult.Empty, LookupResult.Failed -> fallback
                    }
                }
                LookupResult.Empty -> when (
                    val addressLookup = requestJson(
                        configuredEndpoint,
                        latitude,
                        longitude,
                        layer = "address",
                    )
                ) {
                    is LookupResult.Found -> runCatching {
                        parse(addressLookup.json)
                    }.getOrNull() ?: fallback
                    LookupResult.Empty, LookupResult.Failed -> fallback
                }
                LookupResult.Failed -> fallback
            }

            // Consent may be withdrawn while a provider is replying. Do not retain a late
            // enrichment result after that boundary, including a newly private saved place.
            if (endpoint() != configuredEndpoint || !permitsLookup(latitude, longitude)) {
                return@withContext fallback
            }
            if (resolved != fallback) try {
                geocodeDao.put(
                    GeocodeCache(key = key, displayName = resolved.address, placeName = resolved.name),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A derived cache failure must not discard the already observed stop.
            }
            resolved
        }

    private suspend fun requestJson(
        configuredEndpoint: String,
        latitude: Double,
        longitude: Double,
        layer: String,
    ): LookupResult {
        throttle.awaitTurn()
        // Recheck after the throttle; privacy settings may have changed while waiting.
        if (endpoint() != configuredEndpoint || !permitsLookup(latitude, longitude)) {
            return LookupResult.Failed
        }
        val url = String.format(
            Locale.US,
            "%s?lat=%.6f&lon=%.6f&format=jsonv2&addressdetails=1&namedetails=1&extratags=1&zoom=18&layer=%s",
            configuredEndpoint,
            latitude,
            longitude,
            layer,
        )
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "DailyBeat (+https://github.com/sampathmannam/dailybeat)")
            .header("Accept-Language", "en")
            .build()
        return awaitLookup(request)
    }

    /** Cancelling capture releases the socket and stalled body read, not just its coroutine. */
    private suspend fun awaitLookup(request: Request): LookupResult = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resume(LookupResult.Failed)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use {
                        when {
                            it.code == 404 -> LookupResult.Empty
                            !it.isSuccessful -> LookupResult.Failed
                            else -> {
                                val body = it.body
                                if (body == null) LookupResult.Empty
                                else if (body.contentLength() > MAX_RESPONSE_BYTES) LookupResult.Failed
                                else {
                                    val source = body.source()
                                    if (source.request(MAX_RESPONSE_BYTES + 1L)) LookupResult.Failed
                                    else {
                                        val payload = source.readUtf8()
                                        // org.json recursively parses nested values. A small but
                                        // deeply nested provider response must not overflow its stack.
                                        if (!isBoundedGeocoderJson(payload)) LookupResult.Failed
                                        else {
                                            val json = JSONObject(payload)
                                            if (json.has("error")) LookupResult.Empty else LookupResult.Found(json)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    LookupResult.Failed
                }
                if (continuation.isActive) continuation.resume(result)
            }
        })
    }

    internal fun parse(json: JSONObject): ResolvedPlace {
        val address = json.optString("display_name").trimOrNull()
            ?.let { InputPolicy.bounded(it, MAX_ADDRESS_CHARS) }
            ?: fallbackLabel()
        // Address-layer feature names often name the road/town, not a visited venue.
        return ResolvedPlace(name = null, address = address)
    }

    private fun parsePoi(json: JSONObject): ResolvedPlace? {
        val category = json.optString("category").trimOrNull()
            ?: json.optString("class").trimOrNull()
        if (category !in POI_CATEGORIES) return null
        val name = extractPoiName(json) ?: return null
        val address = json.optString("display_name").trimOrNull()
            ?.let { InputPolicy.bounded(it, MAX_ADDRESS_CHARS) }
            ?: name
        return ResolvedPlace(
            name = InputPolicy.bounded("Near $name", MAX_NAME_CHARS),
            address = InputPolicy.bounded("Near $address", MAX_ADDRESS_CHARS),
        )
    }

    private fun extractPoiName(json: JSONObject): String? {
        // The result's own localized name outranks its chain brand or legal operator.
        json.optString("name").trimOrNull()?.let {
            return InputPolicy.bounded(it, MAX_NAME_CHARS)
        }
        json.optJSONObject("namedetails")?.let { names ->
            listOf("name:en", "name").forEach { key ->
                names.optString(key).trimOrNull()?.let {
                    return InputPolicy.bounded(it, MAX_NAME_CHARS)
                }
            }
        }
        json.optJSONObject("address")?.let { address ->
            NAMED_POI_KEYS.forEach { key ->
                address.optString(key).trimOrNull()?.let {
                    return InputPolicy.bounded(it, MAX_NAME_CHARS)
                }
            }
        }
        listOf("namedetails", "extratags").forEach { objectName ->
            json.optJSONObject(objectName)?.optString("brand").trimOrNull()?.let {
                return InputPolicy.bounded(it, MAX_NAME_CHARS)
            }
        }
        return null
    }

    private fun String?.trimOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun cacheKey(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.4f,%.4f", lat, lon)

    private fun JSONObject.isNear(latitude: Double, longitude: Double): Boolean {
        val resultLatitude = optString("lat").toDoubleOrNull() ?: return false
        val resultLongitude = optString("lon").toDoubleOrNull() ?: return false
        if (!isValidCoordinate(resultLatitude, resultLongitude)) return false
        return distanceMeters(latitude, longitude, resultLatitude, resultLongitude) <= MAX_POI_DISTANCE_M
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
        return 6_371_000.0 * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    private fun fallbackLabel(): String =
        "Unnamed place"

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    private companion object {
        const val MAX_NAME_CHARS = 80
        const val MAX_ADDRESS_CHARS = 1_000
        const val MAX_RESPONSE_BYTES = 1L * 1024L * 1024L
        // Conservative product heuristic, not a confidence score or proof of entry.
        const val MAX_POI_DISTANCE_M = 50.0
        const val MAX_CACHE_AGE_MS = 30L * 24 * 60 * 60 * 1_000
        val POI_CATEGORIES = setOf(
            "amenity", "shop", "office", "tourism", "leisure", "historic",
            "healthcare", "military", "building",
        )

        sealed interface LookupResult {
            data class Found(val json: JSONObject) : LookupResult
            data object Empty : LookupResult
            data object Failed : LookupResult
        }

        /**
         * Address keys that name a place rather than locate it, most specific first. Ordered so
         * a police station wins over the road it is on and the town it is in.
         */
        val NAMED_POI_KEYS = listOf(
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
        )

    }
}

/** Wall-clock changes must never extend a lookup wait or block capture for hours. */
internal class GeocoderRequestThrottle(
    minimumRequestIntervalMs: Long,
    private val elapsedRealtimeMs: () -> Long,
    private val wait: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    // This is an internal request-spacing setting, not a provider Retry-After deadline.
    // Bound even invalid configuration and a broken/injected clock to a finite single wait.
    private val intervalMs = minimumRequestIntervalMs.coerceIn(0L, 60_000L)
    private val mutex = Mutex()
    private var lastRequestMs: Long? = null

    suspend fun awaitTurn() = mutex.withLock {
        val now = elapsedRealtimeMs()
        val previous = lastRequestMs
        val elapsed = when {
            previous == null -> intervalMs
            now < 0L || previous < 0L || now < previous -> 0L
            else -> now - previous // Nonnegative ordered values cannot overflow subtraction.
        }
        val remaining = intervalMs - elapsed.coerceAtMost(intervalMs)
        if (remaining > 0L) wait(remaining)
        lastRequestMs = elapsedRealtimeMs()
    }
}

internal fun isBoundedGeocoderJson(payload: String): Boolean = isBoundedJson(payload, objectOnly = true)
