package com.dailybeat.app.data.repo

import com.dailybeat.app.data.db.PlaceDao
import com.dailybeat.app.data.model.Place
import kotlinx.coroutines.flow.Flow

class PlaceRepository(private val placeDao: PlaceDao) {

    fun observeAll(): Flow<List<Place>> = placeDao.observeAll()

    suspend fun all(): List<Place> = placeDao.all()

    suspend fun add(name: String, latitude: Double, longitude: Double, radiusM: Int = 100) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        placeDao.insert(Place(name = trimmed, latitude = latitude, longitude = longitude, radiusM = radiusM))
    }

    suspend fun delete(place: Place) = placeDao.delete(place)
}
