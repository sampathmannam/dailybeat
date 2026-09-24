package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.PlaceDao
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.util.InputPolicy
import kotlinx.coroutines.flow.Flow

class PlaceRepository(private val placeDao: PlaceDao) {

    fun observeAll(): Flow<List<Place>> = placeDao.observeAll()

    suspend fun all(): List<Place> = placeDao.all()

    suspend fun add(name: String, latitude: Double, longitude: Double, radiusM: Int = 100) {
        val trimmed = InputPolicy.singleLine(name, InputPolicy.PLACE_NAME_CHARS).trim()
        if (trimmed.isEmpty()) return
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Enter a valid location." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Enter a valid location." }
        placeDao.insert(
            Place(
                name = trimmed,
                latitude = latitude,
                longitude = longitude,
                radiusM = radiusM.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M),
            ),
        )
    }

    suspend fun delete(place: Place) {
        check(placeDao.deleteIfCurrent(place.id, place.name, place.latitude, place.longitude,
            place.radiusM, place.isPrivate) == 1) { STALE_PLACE_MESSAGE }
    }

    /**
     * Called inside the naming workflow's Room transaction. A repeated naming action may
     * update its exact saved point, never a neighbouring geofence or an ambiguous old row.
     * Returns false only when there is no saved point at these exact coordinates.
     */
    suspend fun renameExactMatch(
        expectedName: String,
        latitude: Double,
        longitude: Double,
        name: String,
    ): Boolean {
        val exactPlaces = placeDao.all().filter { it.latitude == latitude && it.longitude == longitude }
        if (exactPlaces.isEmpty()) return false
        val exact = exactPlaces.singleOrNull { it.name.trim() == expectedName.trim() }
        check(exactPlaces.size == 1 && exact != null) {
            "This saved place changed. Reopen the day and try again."
        }
        val trimmed = InputPolicy.singleLine(name, InputPolicy.PLACE_NAME_CHARS).trim()
        require(trimmed.isNotEmpty()) { "Enter a place name." }
        check(placeDao.renameIfCurrent(exact.id, exact.name, exact.latitude, exact.longitude,
            exact.radiusM, exact.isPrivate, trimmed) == 1) { STALE_PLACE_MESSAGE }
        return true
    }

    /** A stale Settings row must never overwrite a newer name, location or privacy choice. */
    suspend fun setPrivate(place: Place, isPrivate: Boolean) {
        check(placeDao.setPrivateIfCurrent(place.id, place.name, place.latitude, place.longitude,
            place.radiusM, place.isPrivate, isPrivate) == 1) { STALE_PLACE_MESSAGE }
    }

    private companion object {
        const val STALE_PLACE_MESSAGE = "This saved place changed. Refresh Settings and try again."
        const val MIN_RADIUS_M = 25
        const val MAX_RADIUS_M = 10_000
    }
}
