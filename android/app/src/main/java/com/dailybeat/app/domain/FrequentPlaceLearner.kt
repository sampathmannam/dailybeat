package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.util.InputPolicy
import kotlin.math.cos

data class PlaceSuggestion(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
)

object FrequentPlaceLearner {

    private const val CLUSTER_RADIUS_M = 100.0
    private const val MIN_VISITS = 3

    fun suggest(visits: List<LocationVisit>, existingPlaces: List<Place>): List<PlaceSuggestion> {
        // These remain suggestions that require the user's approval, including captures not yet
        // reviewed. Hidden stops, corrupt coordinates and already saved/private places must not
        // become new naming suggestions.
        val dwells = visits.filter {
            it.visitType == "dwell" && !it.hidden && it.endMs > it.startMs &&
                it.latitude.isFinite() && it.latitude in -90.0..90.0 &&
                it.longitude.isFinite() && it.longitude in -180.0..180.0 &&
                !(it.latitude == 0.0 && it.longitude == 0.0) &&
                GeofenceMatcher.matchPlace(it.latitude, it.longitude, existingPlaces) == null
        }
        if (dwells.size < MIN_VISITS) return emptyList()

        val clusters = mutableListOf<List<LocationVisit>>()
        val used = mutableSetOf<Long>()

        for (visit in dwells) {
            if (visit.id in used) continue
            val cluster = mutableListOf(visit)
            for (other in dwells) {
                if (other.id == visit.id || other.id in used) continue
                // Complete-link clustering prevents a chain of adjacent shops from turning into
                // one fictional midpoint. Conflicting names also belong to separate candidates.
                if (cluster.all { member ->
                        distanceM(member.latitude, member.longitude, other.latitude, other.longitude) <= CLUSTER_RADIUS_M &&
                            compatibleNames(member.placeName, other.placeName)
                    }) {
                    cluster += other
                }
            }
            if (cluster.size >= MIN_VISITS) {
                clusters += cluster
                cluster.forEach { used += it.id }
            }
        }

        return clusters.mapNotNull { cluster ->
            val lat = cluster.map { it.latitude }.average()
            val lon = cluster.map { it.longitude }.average()
            if (existingPlaces.any { distanceM(lat, lon, it.latitude, it.longitude) <= it.radiusM.toDouble() }) {
                return@mapNotNull null
            }
            // A road address is not a venue name. Keep approximate "Near …" names qualified.
            val name = cluster.mapNotNull { meaningfulName(it.placeName) }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }
                ?.takeIf { it.value > cluster.size / 2 }
                ?.key
                ?: "Frequent location"
            PlaceSuggestion(
                name = InputPolicy.bounded(name, 80),
                latitude = lat,
                longitude = lon,
                visitCount = cluster.size,
            )
        }
    }

    private fun meaningfulName(name: String?): String? = name?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals("Unnamed place", ignoreCase = true) &&
            !it.equals("Frequent location", ignoreCase = true)
    }

    private fun compatibleNames(first: String?, second: String?): Boolean {
        val firstName = meaningfulName(first)
        val secondName = meaningfulName(second)
        return firstName == null || secondName == null || firstName.equals(secondName, ignoreCase = true)
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = (kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)).coerceIn(0.0, 1.0)
        return earth * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
