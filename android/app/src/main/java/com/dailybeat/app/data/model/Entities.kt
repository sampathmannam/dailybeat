package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [Index(value = ["timestamp"])],
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val rawText: String,
    val placeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val peopleMentioned: String? = null,
    val caseNumbers: String? = null,
    val sourceId: String? = null,
)

@Entity(tableName = "places")
data class Place(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int = 100,
)
