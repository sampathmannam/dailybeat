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

    suspend fun delete(place: Place) = placeDao.delete(place)

    suspend fun setPrivate(place: Place, isPrivate: Boolean) =
        placeDao.update(place.copy(isPrivate = isPrivate))

    private companion object {
        const val MIN_RADIUS_M = 25
        const val MAX_RADIUS_M = 10_000
    }
}
