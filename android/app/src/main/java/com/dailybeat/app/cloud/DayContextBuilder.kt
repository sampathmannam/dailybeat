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

    fun build(
        date: LocalDate,
        officerName: String,
        visits: List<LocationVisit>,
        events: List<Event>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val sections = mutableListOf<String>()
        sections += "OFFICER: $officerName"
        sections += "DATE: ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}"

        if (visits.isNotEmpty()) {
            sections += "LOCATION TIMELINE (passive GPS + OpenStreetMap):"
            visits.forEach { visit ->
                sections += formatVisit(visit, zone)
            }
        } else {
            sections += "LOCATION TIMELINE: No visit segments recorded yet. GPS may still be collecting."
        }

        val calls = events.filter { it.type == "call" }
        if (calls.isNotEmpty()) {
            sections += "PHONE CALLS:"
            calls.forEach { e -> sections += "- ${formatTime(e.timestamp, zone)}: ${e.rawText}" }
        }

        val voice = events.filter { it.type == "voice" }
        if (voice.isNotEmpty()) {
            sections += "VOICE NOTES:"
            voice.forEach { e -> sections += "- ${formatTime(e.timestamp, zone)}: ${e.rawText}" }
        }

        val manual = events.filter { it.type == "manual" }
        if (manual.isNotEmpty()) {
            sections += "OFFICER NOTES:"
            manual.forEach { e -> sections += "- ${formatTime(e.timestamp, zone)}: ${e.rawText}" }
        }

        return sections.joinToString("\n")
    }

    private fun formatVisit(visit: LocationVisit, zone: ZoneId): String {
        val start = formatTime(visit.startMs, zone)
        val end = formatTime(visit.endMs, zone)
        val durationMin = ((visit.endMs - visit.startMs) / 60000.0).roundToInt()
        val label = when (visit.visitType) {
            "transit" -> "Transit near ${visit.address ?: "route"}"
            else -> visit.placeName ?: visit.address ?: "Unknown place"
        }
        val coords = String.format(Locale.US, "(%.4f, %.4f)", visit.latitude, visit.longitude)
        return "- $start–$end (${durationMin} min): $label $coords"
    }

    private fun formatTime(epochMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).format(timeFmt)

    const val SYSTEM_PROMPT =
        "You are an expert assistant for an Indian Police Service officer writing the official daily diary. " +
        "You receive PASSIVE DATA: GPS visit timeline (where they stayed and traveled, with durations), " +
        "phone calls, and optional voice or typed notes. " +
        "Write a formal, factual daily diary report in standard IPS format. " +
        "Use only information provided. Do not invent meetings, people, or cases. " +
        "Use 24-hour times. Structure: brief overview, then chronological narrative, then closing line. " +
        "If data is sparse, state what was observed and what is missing."
}
