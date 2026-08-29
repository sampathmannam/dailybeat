package com.dailybeat.app.cloud

import com.dailybeat.app.data.model.Event
import com.dailybeat.app.data.model.LocationVisit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object DayContextBuilder {

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    data class BuiltContext(
        val text: String,
        val visitRefCount: Int,
        val eventRefCount: Int,
    )

    fun build(
        date: LocalDate,
        officerName: String,
        visits: List<LocationVisit>,
        events: List<Event>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildDetailed(date, officerName, visits, events, zone).text

    fun buildDetailed(
        date: LocalDate,
        officerName: String,
        visits: List<LocationVisit>,
        events: List<Event>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): BuiltContext {
        val sections = mutableListOf<String>()
        sections += "OFFICER: $officerName"
        sections += "DATE: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"
        sections += "CITATION RULE: Reference items as [V#] for visits and [E#] for events in your report."

        if (visits.isNotEmpty()) {
            sections += "LOCATION TIMELINE (passive GPS + OpenStreetMap):"
            visits.forEachIndexed { index, visit ->
                sections += formatVisitRef(index + 1, visit, zone)
            }
        } else {
            sections += "LOCATION TIMELINE: No visit segments recorded yet."
        }

        val notableEvents = events.filter { it.type in listOf("call", "voice", "manual", "moment") }
        if (notableEvents.isNotEmpty()) {
            sections += "EVENTS:"
            notableEvents.forEachIndexed { index, event ->
                sections += formatEventRef(index + 1, event, zone)
            }
        }

        return BuiltContext(
            text = sections.joinToString("\n"),
            visitRefCount = visits.size,
            eventRefCount = notableEvents.size,
        )
    }

    private fun formatVisitRef(ref: Int, visit: LocationVisit, zone: ZoneId): String {
        val start = formatTime(visit.startMs, zone)
        val end = formatTime(visit.endMs, zone)
        val durationMin = ((visit.endMs - visit.startMs) / 60000.0).roundToInt()
        val label = when (visit.visitType) {
            "transit" -> "Transit near ${visit.address ?: "route"}"
            else -> visit.placeName ?: visit.address ?: "Unknown place"
        }
        val coords = String.format(Locale.US, "(%.4f, %.4f)", visit.latitude, visit.longitude)
        return "[V$ref] $start–$end (${durationMin} min): $label $coords"
    }

    private fun formatEventRef(ref: Int, event: Event, zone: ZoneId): String {
        val time = formatTime(event.timestamp, zone)
        val typeLabel = event.type.uppercase(Locale.getDefault())
        return "[E$ref] $time ($typeLabel): ${event.rawText}"
    }

    private fun formatTime(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFmt)

    const val SYSTEM_PROMPT =
        "You are an expert assistant for an Indian Police Service officer writing the official daily diary. " +
        "You receive PASSIVE DATA with citation IDs: [V1],[V2] for GPS visits and [E1],[E2] for calls/voice/notes. " +
        "Write a formal, factual daily diary in standard IPS format. " +
        "INLINE CITATIONS REQUIRED: after each factual sentence, cite sources like [V2][E1]. " +
        "Use only provided data. Do not invent meetings, people, or cases. " +
        "Use 24-hour times. Structure: overview, chronological narrative, closing line."
}
