package com.dailybeat.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A user-visible checkpoint captured before a diary is replaced or a new editing session starts. */
@Entity(
    tableName = "diary_revisions",
    indices = [Index(value = ["dateKey", "createdAt"])],
)
data class DiaryRevision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,
    val text: String,
    val createdAt: Long,
    val reason: String,
)

/** Immutable audit record for a correction the user makes to a captured visit. */
@Entity(
    tableName = "visit_corrections",
    indices = [Index(value = ["visitId", "correctedAt"])],
)
data class VisitCorrection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val visitId: Long,
    val correctedAt: Long,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
)
