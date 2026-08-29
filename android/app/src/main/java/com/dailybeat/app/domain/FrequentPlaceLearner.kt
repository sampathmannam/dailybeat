package com.dailybeat.app.domain

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.data.model.Place
import kotlin.math.cos

data class PlaceSuggestion(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val visitCount: Int,
)

object FrequentPlaceLearner {

    private const val CLUSTER_RADIUS_M = 200.0
    private const val MIN_VISITS = 3

    fun suggest(visits: List<LocationVisit>, existingPlaces: List<Place>): List<PlaceSuggestion> {
        val dwells = visits.filter { it.visitType != "transit" }
        if (dwells.size < MIN_VISITS) return emptyList()

        val clusters = mutableListOf<List<LocationVisit>>()
        val used = mutableSetOf<Long>()

        for (visit in dwells) {
            if (visit.id in used) continue
            val cluster = dwells.filter { other ->
                other.id !in used && distanceM(visit.latitude, visit.longitude, other.latitude, other.longitude) <= CLUSTER_RADIUS_M
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
            val fallbackName = cluster.first().address
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
                .ifBlank { "Frequent location" }
            val name = cluster.mapNotNull { it.placeName }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: fallbackName
            PlaceSuggestion(
                name = name.take(80),
                latitude = lat,
                longitude = lon,
                visitCount = cluster.size,
            )
        }
    }

    private fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = (lat2 - lat1) * Math.PI / 180.0
        val dLon = (lon2 - lon1) * Math.PI / 180.0
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earth * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
