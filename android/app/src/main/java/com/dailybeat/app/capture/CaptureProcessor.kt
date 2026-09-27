package com.dailybeat.app.capture

import androidx.room.withTransaction
import com.dailybeat.app.data.db.CaptureCheckpoint
import com.dailybeat.app.data.db.CaptureFix
import com.dailybeat.app.data.db.DailyBeatDb
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationBreadcrumb
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.repo.PlaceRepository
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.geo.OsmGeocoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.time.LocalDate

/** Also held during restore/erase: a delayed capture callback cannot resurrect replaced data. */
object CaptureStorageGate {
    val mutex = Mutex()
    val generation = java.util.concurrent.atomic.AtomicLong(0)
    /** Erase/restore/retention change personal data; ordinary GPS pause does not invalidate editing. */
    val dataGeneration = java.util.concurrent.atomic.AtomicLong(0)
    private val _dataChanges = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val dataChanges: kotlinx.coroutines.flow.StateFlow<Long> = _dataChanges
    private data class RetentionChange(val generation: Long, val fromGeneration: Long, val retainedFrom: LocalDate)
    @Volatile private var retentionChange: RetentionChange? = null

    /** Call under mutex only after replacement commits (or before an irreversible erase). */
    fun invalidatePersonalData(retainedFrom: LocalDate? = null) {
        val previousGeneration = dataGeneration.get()
        val previousRetention = retentionChange?.takeIf { it.generation == previousGeneration }
        // Publish the boundary before the new generation becomes visible. A full erase/restore
        // clears it, so a retained-date exception can never promote a pre-replacement draft.
        retentionChange = retainedFrom?.let {
            RetentionChange(previousGeneration + 1L, previousRetention?.fromGeneration ?: previousGeneration,
                maxOf(it, previousRetention?.retainedFrom ?: it))
        }
        _dataChanges.value = dataGeneration.incrementAndGet()
    }

    /** Only manual, date-scoped drafts may survive deletions of other, older days. */
    internal fun generationForRetainedDate(expected: Long, date: LocalDate): Long {
        val retention = retentionChange ?: return expected
        val current = dataGeneration.get()
        return if (retention.generation == current && expected >= retention.fromGeneration &&
            expected < current && date >= retention.retainedFrom) current else expected
    }

    suspend fun <T> writeIfCurrent(expected: Long, retainedDate: LocalDate? = null,
                                  write: suspend () -> T): T = mutex.withLock {
        val effective = retainedDate?.let { generationForRetainedDate(expected, it) } ?: expected
        check(effective == dataGeneration.get()) { "Local data changed. Review this day again before saving." }
        write()
    }
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
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Validate before SQLite binding: NaN is bound as NULL and would abort a whole GPS batch. */
    suspend fun enqueue(fixes: List<CaptureFix>, onRejected: (Long, String) -> Unit = { _, _ -> }) {
        val nowMs = clock()
        val accepted = fixes.filter { fix ->
            val decision = LocationQualityFilter.assess(fix.toSample(), previous = null, nowMs = nowMs)
            if (!decision.accepted) onRejected(fix.timestampMs, decision.reason ?: "Unreliable location")
            decision.accepted
        }
        if (accepted.isNotEmpty()) db.captureJournal().enqueue(accepted)
    }

    suspend fun drain(
        onRejected: (Long, String) -> Unit = { _, _ -> },
        allowNetworkLookup: Boolean = true,
        onAccepted: (LocationSample, String) -> Unit = { _, _ -> },
    ) {
        val journal = db.captureJournal()
        while (true) {
            val batch = journal.pending()
            if (batch.isEmpty()) return
            for (fix in batch) {
                val nowMs = clock()
                val memory = BufferedCheckpoint(journal.checkpoint(), nowMs)
                val latest = db.breadcrumbs().latestAtOrBefore(LocationQualityFilter.latestAllowedTime(nowMs))
                val previous = latest?.let {
                    LocationSample(it.latitude, it.longitude, it.timestampMs, it.accuracyM)
                }
                val sample = fix.toSample()
                val decision = LocationQualityFilter.assess(sample, previous, nowMs)
                val duplicate = memory.load()?.lastSampleMs?.let { sample.timestampMs <= it } == true
                if (!decision.accepted || duplicate) {
                    journal.acknowledge(fix.timestampMs)
                    if (!decision.accepted) onRejected(sample.timestampMs, decision.reason ?: "Unreliable location")
                    continue
                }
                val visits = java.util.Collections.synchronizedList(mutableListOf<LocationVisit>())
                val tracker = VisitTracker(
                    CoroutineScope(currentCoroutineContext()), PlaceRepository(db.places()), geocoder,
                    onVisitRecorded = { visits += it }, stateStore = memory,
                    allowNetworkLookup = allowNetworkLookup,
                )
                tracker.onLocation(sample.latitude, sample.longitude, sample.timestampMs, accuracyM = sample.accuracyM)
                tracker.awaitPendingWrites()
                val point = if (RoutePointSampler.shouldPersist(sample, previous)) {
                    LocationBreadcrumb(timestampMs = sample.timestampMs, latitude = sample.latitude,
                        longitude = sample.longitude, accuracyM = sample.accuracyM, quality = decision.quality)
                } else null
                db.withTransaction {
                    visits.forEach { visit ->
                        db.visits().insert(visit)
                        db.events().insert(capturedVisitEvent(visit))
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
        val memory = BufferedCheckpoint(journal.checkpoint(), clock())
        memory.load()?.let { memory.save(it.copy(suspended = true)) }
        journal.checkpoint(CaptureCheckpoint(payload = memory.encode()))
    }

    /** A failed explicit stop must be retried as a stop, not as continued observation. */
    suspend fun recover(
        captureAllowed: Boolean,
        onRejected: (Long, String) -> Unit = { _, _ -> },
        onAccepted: (LocationSample, String) -> Unit = { _, _ -> },
    ) {
        if (captureAllowed) {
            drain(onRejected = onRejected, onAccepted = onAccepted)
        } else {
            // Retain and finalize observations already in the inbox without geocoding. Clearing
            // the open interval also prevents a quick resume from including the privacy pause.
            finish(onRejected = onRejected, onAccepted = onAccepted)
        }
    }

    /** Explicit pause/off ends observed history. Battery sleep simply leaves the checkpoint. */
    suspend fun finish(
        onRejected: (Long, String) -> Unit = { _, _ -> },
        onAccepted: (LocationSample, String) -> Unit = { _, _ -> },
    ) {
        drain(onRejected = onRejected, allowNetworkLookup = false, onAccepted = onAccepted)
        // First retain all already-queued observations, then durably record the stop before
        // the final derived-row transaction. If that transaction fails and capture is quickly
        // re-enabled, VisitTracker's suspended branch still separates the next observation.
        // A failure to drain or persist this marker itself cannot guarantee a durable boundary.
        suspendCapture()
        val memory = BufferedCheckpoint(db.captureJournal().checkpoint(), clock())
        val visits = java.util.Collections.synchronizedList(mutableListOf<LocationVisit>())
        val tracker = VisitTracker(CoroutineScope(currentCoroutineContext()), PlaceRepository(db.places()),
            geocoder, onVisitRecorded = { visits += it }, stateStore = memory)
        tracker.flushPending(allowNetworkLookup = false)
        tracker.awaitPendingWrites()
        db.withTransaction {
            visits.forEach { visit ->
                db.visits().insert(visit)
                db.events().insert(capturedVisitEvent(visit))
            }
            db.captureJournal().checkpoint(CaptureCheckpoint(payload = memory.encode()))
        }
    }
}

private fun CaptureFix.toSample() = LocationSample(latitude, longitude, timestampMs, accuracyM, isMock)

/** Keep the visit timeline and moments timeline equally informative. */
internal fun capturedVisitEvent(visit: LocationVisit): Event {
    val place = VisitLabels.name(visit)
    val transit = visit.visitType.equals("transit", ignoreCase = true)
    return Event(
        timestamp = visit.startMs,
        type = "visit",
        rawText = VisitLabels.momentText(transit, place),
        placeName = place,
        latitude = visit.latitude,
        longitude = visit.longitude,
    )
}

internal class BufferedCheckpoint(payload: String? = null, nowMs: Long = System.currentTimeMillis()) : VisitTrackerStateStore {
    private var state: VisitTrackerState? = payload?.let(::decode)?.takeIf { it.isUsable(nowMs) }
    override fun load() = state
    override fun save(state: VisitTrackerState) { this.state = state }
    override fun clear() { state = null }
    fun encode(): String = state?.let { s -> JSONObject().apply {
        put("dwellLat", s.dwellLat); put("dwellLon", s.dwellLon); put("dwellStartMs", s.dwellStartMs)
        put("lastSampleMs", s.lastSampleMs); put("transitStartMs", s.transitStartMs)
        put("transitLat", s.transitLat); put("transitLon", s.transitLon)
        put("maxTransitDisplacementM", s.maxTransitDisplacementM)
        put("departureLat", s.departureLat); put("departureLon", s.departureLon); put("inTransit", s.inTransit); put("suspended", s.suspended)
    }.toString() } ?: "{}"

    private fun decode(payload: String): VisitTrackerState? = try {
        val j = JSONObject(payload)
        if (!j.has("lastSampleMs")) null else {
            fun coordinate(key: String) = if (j.has(key) && !j.isNull(key)) j.getDouble(key) else null
            VisitTrackerState(
                dwellLat = coordinate("dwellLat"),
                dwellLon = coordinate("dwellLon"),
                dwellStartMs = j.getLong("dwellStartMs"),
                lastSampleMs = j.getLong("lastSampleMs"),
                transitStartMs = j.getLong("transitStartMs"),
                transitLat = coordinate("transitLat"),
                transitLon = coordinate("transitLon"),
                departureLat = coordinate("departureLat"),
                departureLon = coordinate("departureLon"),
                inTransit = j.getBoolean("inTransit"),
                suspended = j.optBoolean("suspended", false),
                maxTransitDisplacementM = if (j.has("maxTransitDisplacementM")) j.getDouble("maxTransitDisplacementM") else 0.0,
            )
        }
    } catch (_: org.json.JSONException) {
        // A broken checkpoint must not prevent the durable inbox from being replayed. Finalized
        // visits remain intact; the next trustworthy fix starts a fresh observed interval.
        null
    }
}
