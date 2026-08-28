package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_visits",
    indices = [Index(value = ["startMs"])],
)
data class LocationVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val latitude: Double,
    val longitude: Double,
    val placeName: String? = null,
    val address: String? = null,
    val visitType: String = "dwell",
)

@Entity(tableName = "geocode_cache")
data class GeocodeCache(
    @PrimaryKey val key: String,
    val displayName: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)
