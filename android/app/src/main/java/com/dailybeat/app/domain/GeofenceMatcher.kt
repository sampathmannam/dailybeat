package com.dailybeat.app.domain

import com.dailybeat.app.data.model.Place
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeofenceMatcher {

    fun matchPlace(latitude: Double, longitude: Double, places: List<Place>): Place? {
        if (places.isEmpty()) return null
        var best: Place? = null
        var bestDistance = Double.MAX_VALUE
        for (place in places) {
            val distance = distanceMeters(latitude, longitude, place.latitude, place.longitude)
            if (distance <= place.radiusM && distance < bestDistance) {
                best = place
                bestDistance = distance
            }
        }
        return best
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusM = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }
}
