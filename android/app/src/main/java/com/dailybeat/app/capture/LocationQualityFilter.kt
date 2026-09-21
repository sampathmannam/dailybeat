package com.dailybeat.app.capture

import kotlin.math.cos
import kotlin.math.sqrt

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyM: Float,
    val isMock: Boolean = false,
)

data class LocationQualityDecision(
    val accepted: Boolean,
    val quality: String,
    val reason: String? = null,
)

object LocationQualityFilter {
    fun assess(
        sample: LocationSample,
        previous: LocationSample?,
        nowMs: Long = System.currentTimeMillis(),
    ): LocationQualityDecision {
        if (
            !sample.latitude.isFinite() || !sample.longitude.isFinite() ||
            sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0 ||
            (sample.latitude == 0.0 && sample.longitude == 0.0)
        ) return rejected("invalid-coordinate")
        if (sample.timestampMs <= 0L) return rejected("invalid-time")
        if (sample.timestampMs > latestAllowedTime(nowMs)) return rejected("future-time")
        if (sample.isMock) return rejected("mock-location")
        if (!sample.accuracyM.isFinite() || sample.accuracyM <= 0f) return rejected("missing-accuracy")
        if (sample.accuracyM > MAX_ACCEPTED_ACCURACY_M) return rejected("weak-accuracy")
        if (previous != null && sample.timestampMs <= previous.timestampMs) return rejected("stale-time")

        previous?.let { prior ->
            val elapsedSeconds = (sample.timestampMs - prior.timestampMs) / 1_000.0
            val speedMps = distanceM(
                prior.latitude,
                prior.longitude,
                sample.latitude,
                sample.longitude,
            ) / elapsedSeconds
            if (elapsedSeconds <= 0.0 || !speedMps.isFinite() || speedMps > MAX_PLAUSIBLE_SPEED_MPS) {
                return rejected("implausible-jump")
            }
        }

        val quality = if (sample.accuracyM <= GOOD_ACCURACY_M) "good" else "approximate"
        return LocationQualityDecision(accepted = true, quality = quality)
    }

    private fun rejected(reason: String) =
        LocationQualityDecision(accepted = false, quality = "rejected", reason = reason)

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        // Floating-point rounding near antipodes can otherwise produce a > 1 and NaN distance.
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
        return earth * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }

    const val GOOD_ACCURACY_M = 75f
    const val MAX_ACCEPTED_ACCURACY_M = 250f
    const val MAX_PLAUSIBLE_SPEED_MPS = 100.0
    // Platform timestamps can differ slightly from the wall clock. A corrupt far-future fix
    // must never advance the durable checkpoint and reject every subsequent real location.
    const val MAX_FUTURE_SKEW_MS = 2 * 60 * 1_000L

    internal fun latestAllowedTime(nowMs: Long): Long =
        nowMs.coerceAtMost(Long.MAX_VALUE - MAX_FUTURE_SKEW_MS) + MAX_FUTURE_SKEW_MS
}
