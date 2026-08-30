package com.dailybeat.app.synthetic

import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.util.DateKeys
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Generates a realistic synthetic IPS day for demos, QA, and adversarial UI testing.
 */
object SyntheticDayGenerator {

    data class Result(
        val visitsInserted: Int,
        val eventsInserted: Int,
    )

    suspend fun seedToday(app: DailyBeatApp, seed: Int = 42): Result {
        return seedForDate(app, DateKeys.today(), seed)
    }

    suspend fun seedForDate(app: DailyBeatApp, date: LocalDate, seed: Int = 42): Result {
        val random = Random(seed)
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val sourceId = "synthetic:${date}:$seed"
        if (app.db.events().countBySource("synthetic", sourceId, dayStart) > 0) {
            return Result(visitsInserted = 0, eventsInserted = 0)
        }

        val scenarios = listOf(
            Triple("Police Headquarters", 12.9716, 77.5946),
            Triple("City Court Complex", 12.9758, 77.6012),
            Triple("Patrol sector — MG Road", 12.9740, 77.6090),
            Triple("Community hall meeting", 12.9780, 77.5920),
        )

        var visits = 0
        var events = 0
        var cursor = dayStart + 8 * 60 * 60 * 1000L

        scenarios.forEachIndexed { index, (name, lat, lon) ->
            val dwellMin = 25 + random.nextInt(40)
            val end = cursor + dwellMin * 60_000L
            val visit = LocationVisit(
                startMs = cursor,
                endMs = end,
                latitude = lat + random.nextDouble(-0.0008, 0.0008),
                longitude = lon + random.nextDouble(-0.0008, 0.0008),
                placeName = name,
                address = "$name, Bengaluru",
                visitType = "dwell",
            )
            app.visitRepository.insert(visit)
            app.db.events().insert(
                Event(
                    timestamp = cursor,
                    type = "visit",
                    rawText = "Stay at $name",
                    placeName = name,
                    latitude = visit.latitude,
                    longitude = visit.longitude,
                ),
            )
            visits++
            events++

            if (index < scenarios.size - 1) {
                val transitEnd = end + (8 + random.nextInt(12)) * 60_000L
                app.visitRepository.insert(
                    LocationVisit(
                        startMs = end,
                        endMs = transitEnd,
                        latitude = lat,
                        longitude = lon,
                        address = "En route",
                        visitType = "transit",
                    ),
                )
                visits++
                cursor = transitEnd
            } else {
                cursor = end
            }
        }

        val callTypes = listOf("incoming", "outgoing")
        repeat(3) { i ->
            val ts = dayStart + (10 + i * 2) * 60 * 60 * 1000L
            app.eventRepository.addStructuredEvent(
                com.dailybeat.app.data.model.StructuredEvent(
                    rawText = "${callTypes[i % 2]} call to +91-98${random.nextInt(10000000, 99999999)} (${30 + i * 15}s)",
                    timestamp = ts,
                ),
                type = "call",
            )
            events++
        }

        app.db.events().insert(
            Event(
                timestamp = dayStart,
                type = "synthetic",
                rawText = "Synthetic briefing: sector patrol and court attendance logged for QA.",
                sourceId = sourceId,
            ),
        )
        events++

        return Result(visitsInserted = visits, eventsInserted = events)
    }
}
