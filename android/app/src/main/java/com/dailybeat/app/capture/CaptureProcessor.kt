package com.dailybeat.app.capture

import androidx.room.withTransaction
import com.dailybeat.app.data.db.CaptureCheckpoint
import com.dailybeat.app.data.db.CaptureFix
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.geo.OsmGeocoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject

/** Also held during restore/erase: a delayed capture callback cannot resurrect replaced data. */
object CaptureStorageGate {
    val mutex = Mutex()
    val generation = java.util.concurrent.atomic.AtomicLong(0)
}

/**
 * The inbox survives process death. Each acknowledgement, checkpoint and all derived rows commit
 * atomically. No network or child coroutine runs inside the SQLite transaction. On any failure,
 * the prior checkpoint and inbox remain available for replay; user corrections aren't overwritten.
 * Callers serialize access with CaptureStorageGate.
 */
class CaptureProcessor(
    private val db: DailyBeatDb,
    private val geocoder: OsmGeocoder,
) {
    suspend fun drain(onAccepted: (LocationSample, String) -> Unit = { _, _ -> }) {
        val journal = db.captureJournal()
        while (true) {
            val batch = journal.pending()
            if (batch.isEmpty()) return
            for (fix in batch) {
                val memory = BufferedCheckpoint(journal.checkpoint())
                val latest = db.breadcrumbs().latest()
                val previous = latest?.let {
                    LocationSample(it.latitude, it.longitude, it.timestampMs, it.accuracyM)
                }
                val sample = LocationSample(fix.latitude, fix.longitude, fix.timestampMs, fix.accuracyM, fix.isMock)
                val decision = LocationQualityFilter.assess(sample, previous)
                val duplicate = memory.load()?.lastSampleMs?.let { sample.timestampMs <= it } == true
                if (!decision.accepted || duplicate) {
                    journal.acknowledge(fix.timestampMs)
                    continue
                }
                val visits = java.util.Collections.synchronizedList(mutableListOf<LocationVisit>())
                val tracker = VisitTracker(
                    CoroutineScope(currentCoroutineContext()), PlaceRepository(db.places()), geocoder,
                    onVisitRecorded = { visits += it }, stateStore = memory,
                )
                tracker.onLocation(sample.latitude, sample.longitude, sample.timestampMs)
                tracker.awaitPendingWrites()
                val point = if (RoutePointSampler.shouldPersist(sample, previous)) {
                    LocationBreadcrumb(timestampMs = sample.timestampMs, latitude = sample.latitude,
                        longitude = sample.longitude, accuracyM = sample.accuracyM, quality = decision.quality)
                } else null
                db.withTransaction {
                    visits.forEach { visit ->
                        db.visits().insert(visit)
                        db.events().insert(Event(timestamp = visit.startMs, type = "visit",
                            rawText = if (visit.visitType == "transit") "Transit" else
                                "Stay at ${visit.placeName ?: visit.address ?: "unnamed place"}",
                            placeName = visit.placeName, latitude = visit.latitude, longitude = visit.longitude))
                    }
                    point?.let { db.breadcrumbs().insert(it) }
                    journal.checkpoint(CaptureCheckpoint(payload = memory.encode()))
                    journal.acknowledge(fix.timestampMs)
                }
                onAccepted(sample, decision.quality)
            }
        }
    }

    suspend fun suspendCapture() {
        val journal = db.captureJournal()
        val memory = BufferedCheckpoint(journal.checkpoint())
        memory.load()?.let { memory.save(it.copy(suspended = true)) }
        journal.checkpoint(CaptureCheckpoint(payload = memory.encode()))
    }

    /** Explicit pause/off ends observed history. Battery sleep simply leaves the checkpoint. */
    suspend fun finish() {
        drain()
        val memory = BufferedCheckpoint(db.captureJournal().checkpoint())
        val visits = java.util.Collections.synchronizedList(mutableListOf<LocationVisit>())
        val tracker = VisitTracker(CoroutineScope(currentCoroutineContext()), PlaceRepository(db.places()),
            geocoder, onVisitRecorded = { visits += it }, stateStore = memory)
        tracker.flushPending(allowNetworkLookup = false)
        tracker.awaitPendingWrites()
        db.withTransaction {
            visits.forEach { visit ->
                db.visits().insert(visit)
                db.events().insert(Event(timestamp = visit.startMs, type = "visit", rawText = "Recorded ${visit.visitType}",
                    placeName = visit.placeName, latitude = visit.latitude, longitude = visit.longitude))
            }
            db.captureJournal().checkpoint(CaptureCheckpoint(payload = memory.encode()))
        }
    }
}

internal class BufferedCheckpoint(payload: String? = null) : VisitTrackerStateStore {
    private var state: VisitTrackerState? = payload?.let(::decode)
    override fun load() = state
    override fun save(state: VisitTrackerState) { this.state = state }
    override fun clear() { state = null }
    fun encode(): String = state?.let { s -> JSONObject().apply {
        put("dwellLat", s.dwellLat); put("dwellLon", s.dwellLon); put("dwellStartMs", s.dwellStartMs)
        put("lastSampleMs", s.lastSampleMs); put("transitStartMs", s.transitStartMs)
        put("transitLat", s.transitLat); put("transitLon", s.transitLon)
        put("departureLat", s.departureLat); put("departureLon", s.departureLon); put("inTransit", s.inTransit); put("suspended", s.suspended)
    }.toString() } ?: "{}"

    private fun decode(payload: String): VisitTrackerState? {
        val j = JSONObject(payload)
        if (!j.has("lastSampleMs")) return null
        fun coordinate(key: String) = if (j.has(key) && !j.isNull(key)) j.getDouble(key) else null
        return VisitTrackerState(coordinate("dwellLat"), coordinate("dwellLon"), j.getLong("dwellStartMs"),
            j.getLong("lastSampleMs"), j.getLong("transitStartMs"), coordinate("transitLat"),
            coordinate("transitLon"), coordinate("departureLat"), coordinate("departureLon"), j.getBoolean("inTransit"), j.optBoolean("suspended", false))
    }
}
