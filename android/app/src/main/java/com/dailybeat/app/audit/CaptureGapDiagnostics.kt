package com.dailybeat.app.audit

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureGapAudit
import com.dailybeat.app.capture.CaptureStorageGate
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Explicit local ADB dump. Never uploads, writes records, or reads coordinates/diary content. */
object CaptureGapDiagnostics {
    suspend fun build(app: DailyBeatApp): String = CaptureStorageGate.mutex.withLock {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val timings = app.db.breadcrumbs().timings()
        val health = app.captureHealthStore.health.value
        fun time(value: Long?): Any = value?.let { Instant.ofEpochMilli(it).atZone(zone).toString() }
            ?: JSONObject.NULL
        JSONObject(SupportDiagnostics.build(app)).apply {
            put("containsPersonalRecords", true) // Dates and recording times are private timing metadata.
            put("containsCoordinates", false)
            put("containsDiaryContent", false)
            put("zoneId", zone.id)
            put("generatedAt", time(now))
            put("scope", "Stored-point intervals only; no inference about travel or whether capture was enabled.")
            put("gapThresholdMinutes", CaptureGapAudit.GAP_THRESHOLD_MS / 60_000)
            put("storedPointCount", timings.size)
            put("excludedInvalidOrFutureTimestamps", timings.count { it.timestampMs !in 1..now })
            put("lastObservedFix", time(health.lastFixAtMs.takeIf { it > 0 }))
            put("lastAcceptedFix", time(health.lastStoredAtMs.takeIf { it > 0 }))
            put("lastAccuracyM", health.lastAccuracyM ?: JSONObject.NULL)
            put("lastRejectionReason", health.lastRejectionReason ?: JSONObject.NULL)
            put("rejectedCountToday", health.rejectedCountToday)
            put("storageUnavailable", health.storageUnavailable)
            put("days", JSONArray().apply {
                CaptureGapAudit.days(timings, now, zone).forEach { day ->
                    put(JSONObject().apply {
                        put("date", day.date.toString())
                        put("pointCount", day.pointCount)
                        put("approximatePointCount", day.approximatePointCount)
                        put("firstPoint", time(day.firstPointMs))
                        put("lastPoint", time(day.lastPointMs))
                        put("gapCount", day.gaps.size)
                        put("gapMinutes", day.gaps.sumOf { it.durationMs } / 60_000.0)
                        put("longestGapMinutes", (day.gaps.maxOfOrNull { it.durationMs } ?: 0) / 60_000.0)
                        put("gaps", JSONArray().apply {
                            day.gaps.forEach { gap -> put(JSONObject().apply {
                                put("from", time(gap.startMs)); put("to", time(gap.endMs))
                                put("minutes", gap.durationMs / 60_000.0)
                            }) }
                        })
                    })
                }
            })
        }.toString(2)
    }
}
