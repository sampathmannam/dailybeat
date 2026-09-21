package com.dailybeat.app.capture

import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Persists route evidence, not every radio jitter callback. Visit detection still receives every
 * accepted fix; this gate only reduces Room writes and redundant map vertices.
 */
object RoutePointSampler {
    private const val MIN_ROUTE_DISTANCE_M = 75.0
    private const val MAX_ROUTE_POINT_AGE_MS = 5 * 60 * 1_000L
    private const val MATERIAL_ACCURACY_GAIN_M = 30f

    fun shouldPersist(sample: LocationSample, previousPersisted: LocationSample?): Boolean {
        val previous = previousPersisted ?: return true
        if (sample.timestampMs <= previous.timestampMs) return false
        if (sample.timestampMs - previous.timestampMs >= MAX_ROUTE_POINT_AGE_MS) return true
        if (distanceM(sample, previous) >= MIN_ROUTE_DISTANCE_M) return true
        return previous.accuracyM - sample.accuracyM >= MATERIAL_ACCURACY_GAIN_M
    }

    fun distanceM(one: LocationSample, two: LocationSample): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(one.latitude - two.latitude)
        val dLon = Math.toRadians(one.longitude - two.longitude)
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(Math.toRadians(one.latitude)) * cos(Math.toRadians(two.latitude)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
        return earth * 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
    }
}
