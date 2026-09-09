package com.dailybeat.app.capture

import android.content.Context

data class VisitTrackerState(
    val dwellLat: Double?,
    val dwellLon: Double?,
    val dwellStartMs: Long,
    val lastSampleMs: Long,
    val transitStartMs: Long,
    val transitLat: Double?,
    val transitLon: Double?,
    val departureLat: Double?,
    val departureLon: Double?,
    val inTransit: Boolean,
)

interface VisitTrackerStateStore {
    fun load(): VisitTrackerState?
    fun save(state: VisitTrackerState)
    fun clear()
}

object NoOpVisitTrackerStateStore : VisitTrackerStateStore {
    override fun load(): VisitTrackerState? = null
    override fun save(state: VisitTrackerState) = Unit
    override fun clear() = Unit
}

internal class SharedPreferencesVisitTrackerStateStore(context: Context) : VisitTrackerStateStore {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun load(): VisitTrackerState? = runCatching {
        if (!prefs.getBoolean(KEY_PRESENT, false)) return@runCatching null
        VisitTrackerState(
            dwellLat = prefs.optionalDouble(KEY_DWELL_LAT),
            dwellLon = prefs.optionalDouble(KEY_DWELL_LON),
            dwellStartMs = prefs.getLong(KEY_DWELL_START, 0L),
            lastSampleMs = prefs.getLong(KEY_LAST_SAMPLE, 0L),
            transitStartMs = prefs.getLong(KEY_TRANSIT_START, 0L),
            transitLat = prefs.optionalDouble(KEY_TRANSIT_LAT),
            transitLon = prefs.optionalDouble(KEY_TRANSIT_LON),
            departureLat = prefs.optionalDouble(KEY_DEPARTURE_LAT),
            departureLon = prefs.optionalDouble(KEY_DEPARTURE_LON),
            inTransit = prefs.getBoolean(KEY_IN_TRANSIT, false),
        )
    }.getOrElse {
        clear()
        null
    }

    override fun save(state: VisitTrackerState) {
        runCatching {
            prefs.edit()
                .clear()
                .putBoolean(KEY_PRESENT, true)
                .putOptionalDouble(KEY_DWELL_LAT, state.dwellLat)
                .putOptionalDouble(KEY_DWELL_LON, state.dwellLon)
                .putLong(KEY_DWELL_START, state.dwellStartMs)
                .putLong(KEY_LAST_SAMPLE, state.lastSampleMs)
                .putLong(KEY_TRANSIT_START, state.transitStartMs)
                .putOptionalDouble(KEY_TRANSIT_LAT, state.transitLat)
                .putOptionalDouble(KEY_TRANSIT_LON, state.transitLon)
                .putOptionalDouble(KEY_DEPARTURE_LAT, state.departureLat)
                .putOptionalDouble(KEY_DEPARTURE_LON, state.departureLon)
                .putBoolean(KEY_IN_TRANSIT, state.inTransit)
                .apply()
        }
    }

    override fun clear() {
        runCatching { prefs.edit().clear().apply() }
    }

    private fun android.content.SharedPreferences.optionalDouble(key: String): Double? =
        if (contains(key)) Double.fromBits(getLong(key, 0L)) else null

    private fun android.content.SharedPreferences.Editor.putOptionalDouble(
        key: String,
        value: Double?,
    ): android.content.SharedPreferences.Editor = apply {
        if (value == null) remove(key) else putLong(key, value.toBits())
    }

    private companion object {
        const val FILE_NAME = "dailybeat_visit_tracker_state"
        const val KEY_PRESENT = "present"
        const val KEY_DWELL_LAT = "dwell_lat"
        const val KEY_DWELL_LON = "dwell_lon"
        const val KEY_DWELL_START = "dwell_start"
        const val KEY_LAST_SAMPLE = "last_sample"
        const val KEY_TRANSIT_START = "transit_start"
        const val KEY_TRANSIT_LAT = "transit_lat"
        const val KEY_TRANSIT_LON = "transit_lon"
        const val KEY_DEPARTURE_LAT = "departure_lat"
        const val KEY_DEPARTURE_LON = "departure_lon"
        const val KEY_IN_TRANSIT = "in_transit"
    }
}
