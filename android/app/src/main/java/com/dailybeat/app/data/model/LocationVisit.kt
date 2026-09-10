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
    val reviewState: String = "confirmed",
    val hidden: Boolean = false,
    val manuallyEdited: Boolean = false,
)

@Entity(
    tableName = "location_breadcrumbs",
    indices = [Index(value = ["timestampMs"])],
)
data class LocationBreadcrumb(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Float,
    val quality: String = "good",
)

@Entity(tableName = "beat_reviews")
data class BeatReview(
    @PrimaryKey val dateKey: String,
    val title: String = "",
    val state: String = "live",
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "geocode_cache")
data class GeocodeCache(
    @PrimaryKey val key: String,
    val displayName: String,
    val fetchedAt: Long = System.currentTimeMillis(),
    /** The map's own name for the spot, e.g. "Rasipuram Police Station". Null when unnamed. */
    val placeName: String? = null,
)
