package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.LocationVisit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class MovementWindow {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

internal data class TodayPatternAnalysis(
    val observedDays: Int = 0,
    val todayStops: Int = 0,
    val averageStopsPerDay: Double = 0.0,
    val recurringPlace: String? = null,
    val recurringPlaceVisits: Int = 0,
    val commonMovementWindow: MovementWindow? = null,
    val commonMovementWindowJourneys: Int = 0,
) {
    val hasUsefulHistory: Boolean get() = observedDays >= 2
}

/**
 * Produces a small, explainable pattern summary using only local visit history.
 *
 * Hidden visits never contribute to the analysis. The input is also clipped to the advertised
 * 28-day window so imported or adversarial data cannot quietly change what the dashboard claims.
 */
internal fun buildTodayPatternAnalysis(
    today: LocalDate,
    zoneId: ZoneId,
    todayVisits: List<LocationVisit>,
    recentVisits: List<LocationVisit>,
): TodayPatternAnalysis {
    val firstDay = today.minusDays(27)
    val visibleHistory = recentVisits.filterNot { it.hidden }.filter { visit ->
        visit.localDate(zoneId) in firstDay..today
    }
    val observedDays = visibleHistory.map { it.localDate(zoneId) }.distinct().size
    val dwellVisits = visibleHistory.filterNot { it.isTransit() }
    val recurringPlace = dwellVisits
        .mapNotNull { it.placeName.usablePatternLabel() ?: it.address.usablePatternLabel() }
        .groupingBy { it }
        .eachCount()
        .entries
        .filter { it.value > 1 }
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .firstOrNull()
    val movementWindow = visibleHistory
        .filter { it.isTransit() }
        .groupingBy { it.movementWindow(zoneId) }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<MovementWindow, Int>> { it.value }
                .thenBy { it.key.ordinal },
        )
        .firstOrNull()

    return TodayPatternAnalysis(
        observedDays = observedDays,
        todayStops = todayVisits.count { !it.hidden && !it.isTransit() },
        averageStopsPerDay = if (observedDays == 0) 0.0 else dwellVisits.size.toDouble() / observedDays,
        recurringPlace = recurringPlace?.key,
        recurringPlaceVisits = recurringPlace?.value ?: 0,
        commonMovementWindow = movementWindow?.key,
        commonMovementWindowJourneys = movementWindow?.value ?: 0,
    )
}

private fun LocationVisit.isTransit(): Boolean = visitType.equals("transit", ignoreCase = true)

private fun LocationVisit.localDate(zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(startMs).atZone(zoneId).toLocalDate()

private fun LocationVisit.movementWindow(zoneId: ZoneId): MovementWindow =
    when (Instant.ofEpochMilli(startMs).atZone(zoneId).hour) {
        in 5..11 -> MovementWindow.MORNING
        in 12..16 -> MovementWindow.AFTERNOON
        in 17..21 -> MovementWindow.EVENING
        else -> MovementWindow.NIGHT
    }

private fun String?.usablePatternLabel(): String? = this
    ?.trim()
    ?.takeIf {
        it.isNotEmpty() &&
            !it.equals("Unnamed place", ignoreCase = true) &&
            !it.equals("route", ignoreCase = true)
    }
