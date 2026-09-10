package com.dailybeat.app.capture

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.domain.GeofenceMatcher
import com.dailybeat.app.domain.OutboundVisitFilter
import com.dailybeat.app.geo.OsmGeocoder
import com.dailybeat.app.geo.ResolvedPlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sqrt
import java.util.Locale

/**
 * Passive visit detection: dwell at a place (≥8 min within ~150m) and transit between places.
 */
class VisitTracker(
    private val scope: CoroutineScope,
    private val placeRepository: PlaceRepository,
    private val osmGeocoder: OsmGeocoder,
    private val onVisitRecorded: suspend (LocationVisit) -> Unit,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val stateStore: VisitTrackerStateStore = NoOpVisitTrackerStateStore,
    private val onWriteFailure: (Throwable) -> Unit = {},
) {

    companion object {
        private const val DWELL_RADIUS_M = 150.0
        private const val MOVE_AWAY_M = 250.0
        private const val MIN_DWELL_MS = 8 * 60 * 1000L
        private const val MIN_TRANSIT_MS = 3 * 60 * 1000L
        private const val MAX_SAMPLE_GAP_MS = 6 * 60 * 60 * 1000L
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
    private val pendingWrites = mutableSetOf<Job>()

    init {
        restoreCheckpoint()
    }

    fun onLocation(latitude: Double, longitude: Double, timestampMs: Long) {
        if (!isValidCoordinate(latitude, longitude) || timestampMs <= 0L) return
        if (lastSampleMs > 0L && timestampMs <= lastSampleMs) return
        if (lastSampleMs > 0L && timestampMs - lastSampleMs > MAX_SAMPLE_GAP_MS) {
            flushPending()
            startDwell(latitude, longitude, timestampMs)
            persistCheckpoint()
            return
        }

        try {
            processLocation(latitude, longitude, timestampMs)
        } finally {
            persistCheckpoint()
        }
    }

    private fun processLocation(latitude: Double, longitude: Double, timestampMs: Long) {
        val anchorLat = dwellLat
        val anchorLon = dwellLon
        if (anchorLat == null || anchorLon == null) {
            // No place anchor. A journey already under way must keep running: dropping it here
            // is what used to lose the trip between two stays.
            if (inTransit) {
                transitLat = latitude
                transitLon = longitude
                lastSampleMs = timestampMs
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

        val dwellEndMs = lastSampleMs.takeIf { it > dwellStartMs } ?: timestampMs
        if (!inTransit) {
            inTransit = true
            transitStartMs = dwellEndMs
            departureLat = anchorLat
            departureLon = anchorLon
        }
        transitLat = latitude
        transitLon = longitude

        // The stay ends when we notice the officer has left. Using only the last in-radius
        // sample discarded the entire stay whenever they drove off, because the 75 m update
        // filter produces no in-radius sample on the way out.
        if (dwellStartMs > 0 && dwellEndMs - dwellStartMs >= MIN_DWELL_MS) {
            finalizeDwell(dwellEndMs)
        }
        resetDwell()

        lastSampleMs = timestampMs
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
        launchWrite {
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
        transitStartMs = 0L
        transitLat = null
        transitLon = null
        departureLat = null
        departureLon = null
    }

    private fun finalizeDwell(dwellEndMs: Long) {
        val lat = dwellLat ?: return
        val lon = dwellLon ?: return
        // Read the start time now: resetDwell() zeroes it before the coroutine below gets to
        // run, which used to store every stay at the epoch so it never matched today's date.
        val startMs = dwellStartMs
        if (startMs <= 0 || dwellEndMs - startMs < MIN_DWELL_MS) return
        launchWrite {
            recordDwell(startMs, dwellEndMs, lat, lon)
        }
    }

    private fun resetDwell() {
        dwellLat = null
        dwellLon = null
        dwellStartMs = 0L
    }

    private suspend fun recordDwell(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val places = runCatching { placeRepository.all() }.getOrDefault(emptyList())
        val matched = GeofenceMatcher.matchPlace(lat, lon, places)
        // A private zone is never sent to the geocoder. Resolving it would hand the officer's
        // home, or an informant's meeting point, to a third-party service at capture time —
        // before any outbound filter downstream ever gets a say. Their own saved name is the
        // label, and no third-party address is stored for it.
        val resolved = if (matched?.isPrivate == true) null else resolveSafely(lat, lon)
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                // A place the officer saved themselves outranks whatever the map calls it.
                placeName = matched?.name ?: resolved?.label,
                address = resolved?.address,
                visitType = "dwell",
            ),
        )
    }

    private suspend fun recordTransit(startMs: Long, endMs: Long, lat: Double, lon: Double) {
        val places = runCatching { placeRepository.all() }.getOrDefault(emptyList())
        // Same rule for a transit sample that happens to fall inside a private zone.
        val resolved = if (OutboundVisitFilter.isPrivateLocation(lat, lon, places)) {
            null
        } else {
            resolveSafely(lat, lon)
        }
        onVisitRecorded(
            LocationVisit(
                startMs = startMs,
                endMs = endMs,
                latitude = lat,
                longitude = lon,
                placeName = null,
                address = resolved?.address,
                visitType = "transit",
            ),
        )
    }

    private suspend fun resolveSafely(latitude: Double, longitude: Double): ResolvedPlace =
        runCatching { osmGeocoder.resolve(latitude, longitude) }.getOrElse {
            ResolvedPlace(
                name = null,
                address = String.format(
                    Locale.US,
                    "Location %.4f, %.4f",
                    latitude,
                    longitude,
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
        if (inTransit) {
            val fromLat = departureLat
            val fromLon = departureLon
            val lat = transitLat
            val lon = transitLon
            if (
                fromLat != null && fromLon != null && lat != null && lon != null &&
                lastSampleMs >= transitStartMs &&
                lastSampleMs - transitStartMs >= MIN_TRANSIT_MS &&
                distanceM(lat, lon, fromLat, fromLon) >= MOVE_AWAY_M
            ) {
                val startMs = transitStartMs
                val endMs = lastSampleMs
                launchWrite { recordTransit(startMs, endMs, lat, lon) }
            }
        } else {
            val endMs = lastSampleMs.takeIf { it > dwellStartMs } ?: System.currentTimeMillis()
            finalizeDwell(endMs)
        }
        clearState()
        stateStore.clear()
    }

    suspend fun awaitPendingWrites() {
        while (true) {
            val jobs = synchronized(pendingWrites) { pendingWrites.toList() }
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch(ioContext, start = CoroutineStart.LAZY) { block() }
        synchronized(pendingWrites) { pendingWrites += job }
        job.invokeOnCompletion { error ->
            synchronized(pendingWrites) { pendingWrites -= job }
            if (error != null) onWriteFailure(error)
        }
        job.start()
    }

    private fun restoreCheckpoint() {
        val state = stateStore.load() ?: return
        val coordinatePairsAreComplete = listOf(
            state.dwellLat to state.dwellLon,
            state.transitLat to state.transitLon,
            state.departureLat to state.departureLon,
        ).all { (lat, lon) -> (lat == null) == (lon == null) }
        val coordinates = listOfNotNull(
            state.dwellLat?.let { it to state.dwellLon },
            state.transitLat?.let { it to state.transitLon },
            state.departureLat?.let { it to state.departureLon },
        )
        val valid = state.lastSampleMs > 0L && coordinatePairsAreComplete && coordinates.all { (lat, lon) ->
            lon != null && isValidCoordinate(lat, lon)
        }
        if (!valid) {
            stateStore.clear()
            return
        }
        dwellLat = state.dwellLat
        dwellLon = state.dwellLon
        dwellStartMs = state.dwellStartMs
        lastSampleMs = state.lastSampleMs
        transitStartMs = state.transitStartMs
        transitLat = state.transitLat
        transitLon = state.transitLon
        departureLat = state.departureLat
        departureLon = state.departureLon
        inTransit = state.inTransit
    }

    private fun persistCheckpoint() {
        stateStore.save(
            VisitTrackerState(
                dwellLat = dwellLat,
                dwellLon = dwellLon,
                dwellStartMs = dwellStartMs,
                lastSampleMs = lastSampleMs,
                transitStartMs = transitStartMs,
                transitLat = transitLat,
                transitLon = transitLon,
                departureLat = departureLat,
                departureLon = departureLon,
                inTransit = inTransit,
            ),
        )
    }

    private fun clearState() {
        resetDwell()
        lastSampleMs = 0L
        transitStartMs = 0L
        transitLat = null
        transitLon = null
        departureLat = null
        departureLon = null
        inTransit = false
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
