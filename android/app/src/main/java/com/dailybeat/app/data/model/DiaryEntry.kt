package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diaries")
data class DiaryEntry(
    @PrimaryKey val dateKey: String,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
