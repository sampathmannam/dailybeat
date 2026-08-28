package com.dailybeat.app.capture

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.geo.OsmGeocoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Passive visit detection: dwell at a place (≥8 min within ~150m) and transit between places.
 */
class VisitTracker(
    private val scope: CoroutineScope,
    private val placeRepository: PlaceRepository,
    private val osmGeocoder: OsmGeocoder,
    private val onVisitRecorded: suspend (LocationVisit) -> Unit,
) {

    companion object {
        private const val DWELL_RADIUS_M = 150.0
        private const val MOVE_AWAY_M = 250.0
        private const val MIN_DWELL_MS = 8 * 60 * 1000L
        private const val MIN_TRANSIT_MS = 3 * 60 * 1000L
    }

    private var dwellLat: Double? = null
    private var dwellLon: Double? = null
    private var dwellStartMs: Long = 0L
    private var lastSampleMs: Long = 0L
    private var transitStartMs: Long = 0L
    private var transitLat: Double? = null
    private var transitLon: Double? = null
    private var inTransit = false

    fun onLocation(latitude: Double, longitude: Double, timestampMs: Long) {
        val lat = dwellLat
        val lon = dwellLon
        if (lat == null || lon == null) {
            startDwell(latitude, longitude, timestampMs)
            return
        }

        val distFromDwell = distanceM(latitude, longitude, lat, lon)

        if (distFromDwell <= DWELL_RADIUS_M) {
            dwellLat = (lat + latitude) / 2.0
            dwellLon = (lon + longitude) / 2.0
            lastSampleMs = timestampMs
            inTransit = false
            return
        }

        if (!inTransit) {
            inTransit = true
            transitStartMs = lastSampleMs.takeIf { it > 0 } ?: timestampMs
            transitLat = latitude
            transitLon = longitude
        } else {
            transitLat = latitude
            transitLon = longitude
        }

        val dwellDuration = transitStartMs - dwellStartMs
        if (dwellDuration >= MIN_DWELL_MS && dwellStartMs > 0) {
            finalizeDwell(dwellEndMs = transitStartMs)
        }

        val transitDuration = timestampMs - transitStartMs
        if (inTransit && transitDuration >= MIN_TRANSIT_MS &&
            distanceM(latitude, longitude, lat, lon) >= MOVE_AWAY_M
        ) {
            val tLat = transitLat ?: latitude
            val tLon = transitLon ?: longitude
            scope.launch(Dispatchers.IO) {
                recordTransit(transitStartMs, timestampMs, tLat, tLon)
            }
            inTransit = false
            startDwell(latitude, longitude, timestampMs)
        }
    }

    private fun startDwell(latitude: Double, longitude: Double, timestampMs: Long) {
        dwellLat = latitude
        dwellLon = longitude
        dwellStartMs = timestampMs
        lastSampleMs = timestampMs
        inTransit = false
    }

    private fun finalizeDwell(dwellEndMs: Long) {
        val lat = dwellLat ?: return
        val lon = dwellLon ?: return
        if (dwellEndMs - dwellStartMs < MIN_DWELL_MS) {
            resetDwell()
            return
        }
        scope.launch(Dispatchers.IO) {
            recordDwell(dwellStartMs, dwellEndMs, lat, lon)
        }
        resetDwell()
    }

    private fun resetDwell() {
        dwellLat = null
        dwellLon = null
        dwellStartMs = 0L
    }

    private suspend fun recordDwell(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val places = placeRepository.all()
        val matched = GeofenceMatcher.matchPlace(lat, lon, places)
        val address = osmGeocoder.resolve(lat, lon)
        val placeName = matched?.name ?: extractShortName(address)
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                placeName = placeName,
                address = address,
                visitType = "dwell",
            ),
        )
    }

    private suspend fun recordTransit(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val address = osmGeocoder.resolve(lat, lon)
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                placeName = null,
                address = address,
                visitType = "transit",
            ),
        )
    }

    private fun extractShortName(display: String): String {
        val parts = display.split(",").map { it.trim() }
        return parts.firstOrNull()?.take(80) ?: display.take(80)
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earth * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    /** Flush open dwell when location service stops (e.g. app killed). */
    fun flushPending() {
        val lat = dwellLat
        val lon = dwellLon
        if (lat == null || lon == null || dwellStartMs <= 0) return
        val endMs = lastSampleMs.takeIf { it > dwellStartMs } ?: System.currentTimeMillis()
        if (endMs - dwellStartMs < MIN_DWELL_MS) return
        scope.launch(Dispatchers.IO) {
            recordDwell(dwellStartMs, endMs, lat, lon)
        }
        resetDwell()
    }
}
