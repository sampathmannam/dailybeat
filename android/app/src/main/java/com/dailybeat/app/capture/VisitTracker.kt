package com.dailybeat.app.capture

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.geo.OsmGeocoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
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
    private val ioContext: CoroutineContext = Dispatchers.IO,
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
    private var departureLat: Double? = null
    private var departureLon: Double? = null
    private var inTransit = false

    fun onLocation(latitude: Double, longitude: Double, timestampMs: Long) {
        val anchorLat = dwellLat
        val anchorLon = dwellLon
        if (anchorLat == null || anchorLon == null) {
            // No place anchor. A journey already under way must keep running: dropping it here
            // is what used to lose the trip between two stays.
            if (inTransit) {
                transitLat = latitude
                transitLon = longitude
                finishTransitIfArrived(latitude, longitude, timestampMs)
            } else {
                startDwell(latitude, longitude, timestampMs)
            }
            return
        }

        if (distanceM(latitude, longitude, anchorLat, anchorLon) <= DWELL_RADIUS_M) {
            dwellLat = (anchorLat + latitude) / 2.0
            dwellLon = (anchorLon + longitude) / 2.0
            lastSampleMs = timestampMs
            inTransit = false
            return
        }

        if (!inTransit) {
            inTransit = true
            transitStartMs = lastSampleMs.takeIf { it > dwellStartMs } ?: dwellStartMs
            departureLat = anchorLat
            departureLon = anchorLon
        }
        transitLat = latitude
        transitLon = longitude

        // The stay ends when we notice the officer has left. Using only the last in-radius
        // sample discarded the entire stay whenever they drove off, because the 75 m update
        // filter produces no in-radius sample on the way out.
        val dwellEndMs = lastSampleMs.takeIf { it > dwellStartMs } ?: timestampMs
        if (dwellStartMs > 0 && dwellEndMs - dwellStartMs >= MIN_DWELL_MS) {
            finalizeDwell(dwellEndMs)
        }
        resetDwell()

        finishTransitIfArrived(latitude, longitude, timestampMs)
    }

    private fun finishTransitIfArrived(latitude: Double, longitude: Double, timestampMs: Long) {
        if (!inTransit) return
        val fromLat = departureLat ?: return
        val fromLon = departureLon ?: return
        val movedFarEnough = distanceM(latitude, longitude, fromLat, fromLon) >= MOVE_AWAY_M
        if (timestampMs - transitStartMs < MIN_TRANSIT_MS || !movedFarEnough) return

        val startMs = transitStartMs
        val tLat = transitLat ?: latitude
        val tLon = transitLon ?: longitude
        scope.launch(ioContext) {
            recordTransit(startMs, timestampMs, tLat, tLon)
        }
        inTransit = false
        departureLat = null
        departureLon = null
        startDwell(latitude, longitude, timestampMs)
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
        // Read the start time now: resetDwell() zeroes it before the coroutine below gets to
        // run, which used to store every stay at the epoch so it never matched today's date.
        val startMs = dwellStartMs
        if (startMs <= 0 || dwellEndMs - startMs < MIN_DWELL_MS) return
        scope.launch(ioContext) {
            recordDwell(startMs, dwellEndMs, lat, lon)
        }
    }

    private fun resetDwell() {
        dwellLat = null
        dwellLon = null
        dwellStartMs = 0L
    }

    private suspend fun recordDwell(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val places = placeRepository.all()
        val matched = GeofenceMatcher.matchPlace(lat, lon, places)
        val resolved = osmGeocoder.resolve(lat, lon)
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                // A place the officer saved themselves outranks whatever the map calls it.
                placeName = matched?.name ?: resolved.label,
                address = resolved.address,
                visitType = "dwell",
            ),
        )
    }

    private suspend fun recordTransit(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val resolved = osmGeocoder.resolve(lat, lon)
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                placeName = null,
                address = resolved.address,
                visitType = "transit",
            ),
        )
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
        val endMs = lastSampleMs.takeIf { it > dwellStartMs } ?: System.currentTimeMillis()
        finalizeDwell(endMs)
        resetDwell()
    }
}
