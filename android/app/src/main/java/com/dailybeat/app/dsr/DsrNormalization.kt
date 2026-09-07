package com.dailybeat.app.dsr

import java.security.MessageDigest
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NormalizedStation(
    val code: String,
    val displayName: String,
)

object DsrNormalization {
    private val stations = listOf(
        Regex("Rasipuram\\s+(?:All\\s+Women\\s+Police\\s+Station|AWPS)", RegexOption.IGNORE_CASE) to
            NormalizedStation("AWPS_RPM", "Rasipuram AWPS"),
        Regex("AWPS\\s*(?:RPM|Rasipuram)?", RegexOption.IGNORE_CASE) to
            NormalizedStation("AWPS_RPM", "Rasipuram AWPS"),
        Regex("Traffic\\s*(?:Rpm|Rasipuram)?", RegexOption.IGNORE_CASE) to
            NormalizedStation("TRAFFIC_RPM", "Rasipuram Traffic"),
        Regex("Namagiripet(?:tai)?", RegexOption.IGNORE_CASE) to
            NormalizedStation("NAMAGIRIPET", "Namagiripet"),
        Regex("Mangal(?:a)?puram", RegexOption.IGNORE_CASE) to
            NormalizedStation("MANGALAPURAM", "Mangalapuram"),
        Regex("Belukurichi", RegexOption.IGNORE_CASE) to
            NormalizedStation("BELUKURICHI", "Belukurichi"),
        Regex("Ayilpatty", RegexOption.IGNORE_CASE) to
            NormalizedStation("AYILPATTY", "Ayilpatty"),
        Regex("Vennandur", RegexOption.IGNORE_CASE) to
            NormalizedStation("VENNANDUR", "Vennandur"),
        Regex("Rasipuram", RegexOption.IGNORE_CASE) to
            NormalizedStation("RASIPURAM", "Rasipuram"),
    )

    val stationAlternation: String = stations.joinToString("|") { "(?:${it.first.pattern})" }

    fun station(value: String): NormalizedStation? {
        val clean = whitespace(value)
        return stations.firstOrNull { (pattern, _) -> pattern.matches(clean) }?.second
    }

    fun stationAtStart(line: String): Pair<NormalizedStation, String>? {
        val clean = whitespace(line)
        return stations.firstNotNullOfOrNull { (pattern, station) ->
            val match = pattern.find(clean)
            if (match?.range?.first == 0) {
                station to clean.substring(match.range.last + 1).trim()
            } else {
                null
            }
        }
    }

    fun whitespace(value: String): String = value
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .replace('\u2011', '-')
        .replace(Regex("[\\t ]+"), " ")
        .trim()

    fun isoDate(day: Int, month: Int, rawYear: Int): String? {
        val year = if (rawYear < 100) 2000 + rawYear else rawYear
        return try {
            LocalDate.of(year, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeException) {
            null
        }
    }

    fun monthNumber(name: String): Int? = runCatching {
        Month.valueOf(name.uppercase(Locale.ENGLISH)).value
    }.getOrNull()

    fun stableId(vararg parts: String): String {
        val bytes = parts.joinToString("|").toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }

    fun priorityForCase(head: String, law: String): DsrPriority {
        val value = "$head $law".uppercase(Locale.ENGLISH)
        return when {
            listOf("POCSO", "MURDER", "SC/ST", "CHILD MISSING").any(value::contains) -> DsrPriority.CRITICAL
            listOf("WOMEN & CHILD", "WOMAN & CHILD", "S.HURT", "A.FIRE", "HANGING", "106(1)").any(value::contains) ->
                DsrPriority.HIGH
            listOf("MISSING", "N.FATAL", "NON FATAL", "THEFT", "ROBBERY", "MOLESTATION", "194 BNSS").any(value::contains) ->
                DsrPriority.MEDIUM
            else -> DsrPriority.ROUTINE
        }
    }

    fun priorityForForecast(details: String, crowd: Int?): DsrPriority {
        val value = details.uppercase(Locale.ENGLISH)
        return when {
            listOf("PROTEST", "DEMONSTRATION", "UNTOUCHABILITY", "COMMUNAL").any(value::contains) -> DsrPriority.HIGH
            crowd != null && crowd >= 500 -> DsrPriority.HIGH
            crowd != null && crowd >= 100 -> DsrPriority.MEDIUM
            listOf("PROCESSION", "FESTIVAL", "KABADDI", "EXAM").any(value::contains) -> DsrPriority.MEDIUM
            else -> DsrPriority.ROUTINE
        }
    }
}
