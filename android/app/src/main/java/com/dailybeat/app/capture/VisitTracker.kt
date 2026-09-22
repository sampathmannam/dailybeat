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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.math.cos
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/**
 * Personal journal stop detection: confirm eight observed minutes near a fixed anchor.
 * Arrival is backdated to its first sample only after that stop is confirmed.
 */
class VisitTracker(
    private val scope: CoroutineScope,
    private val placeRepository: PlaceRepository,
    private val osmGeocoder: OsmGeocoder,
    private val onVisitRecorded: suspend (LocationVisit) -> Unit,
    private val ioContext: CoroutineContext = Dispatchers.IO,
    private val stateStore: VisitTrackerStateStore = NoOpVisitTrackerStateStore,
    private val onWriteFailure: (Throwable) -> Unit = {},
    private val allowNetworkLookup: Boolean = true,
) {

    companion object {
        private const val DWELL_RADIUS_M = 150.0
        private const val MOVE_AWAY_M = 250.0
        private const val MIN_DWELL_MS = 8 * 60 * 1000L
        private const val MIN_TRANSIT_MS = 3 * 60 * 1000L
        // A journal policy, not an Android guarantee: current requests batch at most two
        // minutes. Longer blind gaps must not silently become hours spent at a place.
        private const val MAX_SAMPLE_GAP_MS = 10 * 60 * 1000L
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
    private var maxTransitDisplacementM = 0.0
    private var suspended = false
    private val pendingWrites = mutableSetOf<Job>()
    private val writeFailures = mutableListOf<Throwable>()

    init {
        restoreCheckpoint()
    }

    fun onLocation(
        latitude: Double,
        longitude: Double,
        timestampMs: Long,
        accuracyM: Float = LocationQualityFilter.GOOD_ACCURACY_M,
    ) {
        if (!isValidCoordinate(latitude, longitude) || timestampMs <= 0L) return
        // A point whose uncertainty exceeds the entire stop radius can still appear on the
        // approximate route, but cannot establish or end a stop at a specific place.
        if (!accuracyM.isFinite() || accuracyM <= 0f || accuracyM > DWELL_RADIUS_M) return
        if (lastSampleMs > 0L && timestampMs <= lastSampleMs) return
        if (suspended || (lastSampleMs > 0L && timestampMs - lastSampleMs > MAX_SAMPLE_GAP_MS)) {
            flushPending(allowNetworkLookup = false)
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
        if (inTransit) {
            observeArrivalCandidate(latitude, longitude, timestampMs)
            return
        }
        val anchorLat = dwellLat
        val anchorLon = dwellLon
        if (anchorLat == null || anchorLon == null) {
            startDwell(latitude, longitude, timestampMs)
            return
        }

        if (distanceM(latitude, longitude, anchorLat, anchorLon) <= DWELL_RADIUS_M) {
            // Keep the anchor fixed: averaging toward every fix lets a slow walk drag the
            // stop across town while every individual step remains inside the radius.
            lastSampleMs = timestampMs
            return
        }

        val dwellEndMs = lastSampleMs
        inTransit = true
        transitStartMs = dwellEndMs
        departureLat = anchorLat
        departureLon = anchorLon
        transitLat = latitude
        transitLon = longitude
        updateTransitDisplacement(latitude, longitude)

        // A lone arrival fix followed by a far-away fix does not prove a stay in between.
        // Close only through the last observation inside this stop's radius.
        if (dwellStartMs > 0 && dwellEndMs - dwellStartMs >= MIN_DWELL_MS) {
            finalizeDwell(dwellEndMs)
        }
        setArrivalCandidate(latitude, longitude, timestampMs)
        lastSampleMs = timestampMs
    }

    private fun observeArrivalCandidate(latitude: Double, longitude: Double, timestampMs: Long) {
        transitLat = latitude
        transitLon = longitude
        updateTransitDisplacement(latitude, longitude)
        lastSampleMs = timestampMs
        val candidateLat = dwellLat
        val candidateLon = dwellLon
        if (candidateLat == null || candidateLon == null ||
            distanceM(latitude, longitude, candidateLat, candidateLon) > DWELL_RADIUS_M) {
            setArrivalCandidate(latitude, longitude, timestampMs)
            return
        }
        if (timestampMs - dwellStartMs < MIN_DWELL_MS) return

        val startMs = transitStartMs
        val arrivalMs = dwellStartMs
        if (arrivalMs - startMs >= MIN_TRANSIT_MS && maxTransitDisplacementM >= MOVE_AWAY_M) {
            launchWrite { recordTransit(startMs, arrivalMs, candidateLat, candidateLon) }
        }
        // The candidate's start and fixed coordinates are already the confirmed stay. Keeping
        // them avoids charging its first eight minutes to travel or losing them altogether.
        clearTransit()
    }

    private fun updateTransitDisplacement(latitude: Double, longitude: Double) {
        val fromLat = departureLat ?: return
        val fromLon = departureLon ?: return
        // Returning home still counts as a journey when an earlier point established travel.
        maxTransitDisplacementM = maxOf(maxTransitDisplacementM, distanceM(latitude, longitude, fromLat, fromLon))
    }

    private fun setArrivalCandidate(latitude: Double, longitude: Double, timestampMs: Long) {
        dwellLat = latitude
        dwellLon = longitude
        dwellStartMs = timestampMs
    }

    private fun clearTransit() {
        inTransit = false
        transitStartMs = 0L
        transitLat = null
        transitLon = null
        departureLat = null
        departureLon = null
        maxTransitDisplacementM = 0.0
    }

    private fun startDwell(latitude: Double, longitude: Double, timestampMs: Long) {
        setArrivalCandidate(latitude, longitude, timestampMs)
        lastSampleMs = timestampMs
        clearTransit()
    }

    private fun finalizeDwell(dwellEndMs: Long, allowNetworkLookup: Boolean = this.allowNetworkLookup) {
        val lat = dwellLat ?: return
        val lon = dwellLon ?: return
        // Read the start time now: resetDwell() zeroes it before the coroutine below gets to
        // run, which used to store every stay at the epoch so it never matched today's date.
        val startMs = dwellStartMs
        if (startMs <= 0 || dwellEndMs - startMs < MIN_DWELL_MS) return
        launchWrite {
            recordDwell(startMs, dwellEndMs, lat, lon, allowNetworkLookup)
        }
    }

    private fun resetDwell() {
        dwellLat = null
        dwellLon = null
        dwellStartMs = 0L
    }

    private suspend fun recordDwell(
        startMs: Long,
        endMs: Long,
        lat: Double,
        lon: Double,
        allowNetworkLookup: Boolean,
    ) {
        val places = placeRepository.all()
        val matched = GeofenceMatcher.matchPlace(lat, lon, places)
        // A private zone is never sent to the geocoder. Resolving it would hand the officer's
        // home, or an informant's meeting point, to a third-party service at capture time —
        // before any outbound filter downstream ever gets a say. Their own saved name is the
        // label, and no third-party address is stored for it.
        val resolved = when {
            OutboundVisitFilter.isPrivateLocation(lat, lon, places) -> null
            allowNetworkLookup -> resolveSafely(lat, lon)
            else -> coordinateFallback()
        }
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

    private suspend fun recordTransit(
        startMs: Long,
        endMs: Long,
        lat: Double,
        lon: Double,
        allowNetworkLookup: Boolean = this.allowNetworkLookup,
    ) {
        val places = placeRepository.all()
        // Same rule for a transit sample that happens to fall inside a private zone.
        val resolved = when {
            OutboundVisitFilter.isPrivateLocation(lat, lon, places) -> null
            allowNetworkLookup -> resolveSafely(lat, lon)
            else -> coordinateFallback()
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
        try {
            osmGeocoder.resolve(latitude, longitude)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            coordinateFallback()
        }

    private fun coordinateFallback(): ResolvedPlace =
        ResolvedPlace(name = null, address = "Unnamed place")

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
        return earth * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    /** Flush open dwell when location service stops (e.g. app killed). */
    fun flushPending(allowNetworkLookup: Boolean = this.allowNetworkLookup) {
        if (inTransit) {
            val fromLat = departureLat
            val fromLon = departureLon
            val lat = transitLat
            val lon = transitLon
            if (
                fromLat != null && fromLon != null && lat != null && lon != null &&
                lastSampleMs >= transitStartMs &&
                lastSampleMs - transitStartMs >= MIN_TRANSIT_MS &&
                maxTransitDisplacementM >= MOVE_AWAY_M
            ) {
                val startMs = transitStartMs
                val endMs = lastSampleMs
                launchWrite { recordTransit(startMs, endMs, lat, lon, allowNetworkLookup) }
            }
        } else {
            val endMs = lastSampleMs // Unobserved time must never lengthen a stay.
            finalizeDwell(endMs, allowNetworkLookup)
        }
        clearState()
        stateStore.clear()
    }

    suspend fun awaitPendingWrites() {
        while (true) {
            val jobs = synchronized(pendingWrites) { pendingWrites.toList() }
            if (jobs.isEmpty()) {
                synchronized(writeFailures) {
                    writeFailures.firstOrNull()?.let { throw it }
                }
                return
            }
            jobs.joinAll()
        }
    }

    /**
     * Privacy erasure is different from an ordinary service stop: an open stay must not be
     * finalized after the database has been wiped. Cancel in-flight enrichment/writes and remove
     * the durable checkpoint before the erase transaction starts.
     */
    fun discardPending() {
        synchronized(pendingWrites) { pendingWrites.toList() }.forEach { it.cancel() }
        clearState()
        stateStore.clear()
    }

    private fun launchWrite(block: suspend () -> Unit) {
        val job = scope.launch(ioContext, start = CoroutineStart.LAZY) {
            try { block() } catch (error: Throwable) {
                synchronized(writeFailures) { writeFailures += error }
                onWriteFailure(error)
            }
        }
        synchronized(pendingWrites) { pendingWrites += job }
        job.invokeOnCompletion { error ->
            synchronized(pendingWrites) { pendingWrites -= job }
            if (error != null) onWriteFailure(error)
        }
        job.start()
    }

    private fun restoreCheckpoint() {
        val state = stateStore.load() ?: return
        if (!state.isUsable()) {
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
        suspended = state.suspended
        maxTransitDisplacementM = state.maxTransitDisplacementM
        if (inTransit) {
            // Older checkpoints did not retain a maximum; their latest point is still useful.
            transitLat?.let { lat -> transitLon?.let { lon -> updateTransitDisplacement(lat, lon) } }
        }
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
                suspended = suspended,
                maxTransitDisplacementM = maxTransitDisplacementM,
            ),
        )
    }

    private fun clearState() {
        suspended = false
        resetDwell()
        lastSampleMs = 0L
        clearTransit()
    }

    private fun isValidCoordinate(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
