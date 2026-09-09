package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * MIGRATION_2_3 (shipped in v2.0.0) creates index_diaries_dateKey on every real device that has
 * ever upgraded through it. The index must be declared here so Room's compiled schema matches
 * what migration has already put on disk — otherwise Room's startup validation rejects the
 * database as not matching its own migration.
 */
@Entity(tableName = "diaries", indices = [Index(value = ["dateKey"])])
data class DiaryEntry(
    @PrimaryKey val dateKey: String,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
