package com.dailybeat.app.data.db

import androidx.room.Dao
import androidx.room.Query
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class JournalSearchHit(val dateKey: String?, val timestampMs: Long, val snippet: String, val kind: String) {
    fun date(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        dateKey?.let(LocalDate::parse) ?: Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
}

/** On-device substring search. Bound the result set and debounce calls in the ViewModel. */
@Dao
interface JournalSearchDao {
    @Query("""
        SELECT dateKey, CAST(strftime('%s', dateKey) AS INTEGER) * 1000 AS timestampMs,
            substr(text, 1, 240) AS snippet, 'Diary' AS kind
        FROM diaries WHERE text LIKE :pattern ESCAPE '\'
        UNION ALL
        SELECT NULL AS dateKey, timestamp AS timestampMs, substr(rawText, 1, 240) AS snippet, 'Note' AS kind
        FROM events WHERE type != 'visit' AND rawText LIKE :pattern ESCAPE '\'
        UNION ALL
        SELECT NULL AS dateKey, startMs AS timestampMs,
            substr(COALESCE(placeName, address, 'Recorded stop'), 1, 240) AS snippet, 'Place' AS kind
        FROM location_visits WHERE hidden = 0 AND
            (placeName LIKE :pattern ESCAPE '\' OR address LIKE :pattern ESCAPE '\')
        ORDER BY timestampMs DESC LIMIT 100
    """)
    suspend fun search(pattern: String): List<JournalSearchHit>

    companion object {
        fun pattern(query: String): String = "%" + query.trim().take(120)
            .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
    }
}
