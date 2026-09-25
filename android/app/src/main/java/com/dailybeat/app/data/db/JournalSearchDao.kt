package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Query
import com.dailybeat.app.util.InputPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class JournalSearchHit(
    val dateKey: String?, val timestampMs: Long, val snippet: String, val kind: String,
    val latitude: Double? = null, val longitude: Double? = null,
    val address: String? = null, val manuallyEdited: Boolean = false,
) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        dateKey?.let(LocalDate::parse) ?: Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()

    fun displaySnippet(places: List<com.dailybeat.app.data.model.Place>): String = if (kind != "Place") snippet else
        com.dailybeat.app.domain.VisitLabels.displayName(
            com.dailybeat.app.data.model.LocationVisit(
                startMs = timestampMs, endMs = timestampMs,
                latitude = latitude ?: Double.NaN, longitude = longitude ?: Double.NaN,
                placeName = snippet, address = address, manuallyEdited = manuallyEdited,
            ), places,
        )
}

/** On-device substring search. Bound the result set and debounce calls in the ViewModel. */
@Dao
interface JournalSearchDao {
    @Query("""
        SELECT dateKey, CAST(strftime('%s', dateKey) AS INTEGER) * 1000 AS timestampMs,
            substr(text, 1, 240) AS snippet, 'Diary' AS kind,
            NULL AS latitude, NULL AS longitude, NULL AS address, 0 AS manuallyEdited
        FROM diaries WHERE text LIKE :pattern ESCAPE '\'
        UNION ALL
        SELECT NULL AS dateKey, timestamp AS timestampMs, substr(rawText, 1, 240) AS snippet, 'Note' AS kind,
            NULL AS latitude, NULL AS longitude, NULL AS address, 0 AS manuallyEdited
        FROM events WHERE type != 'visit' AND rawText LIKE :pattern ESCAPE '\'
        UNION ALL
        SELECT NULL AS dateKey, startMs AS timestampMs,
            substr(COALESCE(placeName, address, ''), 1, 240) AS snippet, 'Place' AS kind,
            latitude, longitude, address, manuallyEdited
        FROM location_visits WHERE hidden = 0 AND
            (placeName LIKE :pattern ESCAPE '\' OR address LIKE :pattern ESCAPE '\')
        ORDER BY timestampMs DESC LIMIT 100
    """)
    suspend fun search(pattern: String): List<JournalSearchHit>

    companion object {
        fun pattern(query: String): String = "%" + InputPolicy.bounded(query.trim(), 120)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
