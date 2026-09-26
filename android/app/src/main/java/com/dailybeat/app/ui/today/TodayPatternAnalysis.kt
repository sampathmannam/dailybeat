package com.dailybeat.app.ui.today

import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.domain.VisitLabels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class MovementWindow {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

internal enum class PatternSuggestion {
    NAME_STOPS,
    NOTE_RECURRING_PLACE,
    REVIEW_AFTER_MOVEMENT,
    REVIEW_BUSY_DAY,
}

internal data class TodayPatternAnalysis(
    val observedDays: Int = 0,
    val todayStops: Int = 0,
    val averageStopsPerDay: Double = 0.0,
    val recurringPlace: String? = null,
    val recurringPlaceVisits: Int = 0,
    val commonMovementWindow: MovementWindow? = null,
    val commonMovementWindowJourneys: Int = 0,
    val stopsToName: Int = 0,
    val suggestions: List<PatternSuggestion> = emptyList(),
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
        .mapNotNull { visit -> visit.patternLabel()?.let { it to visit.localDate(zoneId) } }
        .groupBy({ it.first }, { it.second })
        // Repeated GPS fragments on one day do not establish a recurring routine.
        .filterValues { dates -> dates.distinct().size >= 2 }
        .mapValues { it.value.size }
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

    val visibleToday = todayVisits.filter { !it.hidden && it.localDate(zoneId) == today }
    val todayStops = visibleToday.count { !it.isTransit() }
    val stopsToName = visibleToday.count { !it.isTransit() && it.patternLabel() == null }
    val priorVisits = visibleHistory.filter { it.localDate(zoneId) < today }
    val priorDays = priorVisits.map { it.localDate(zoneId) }.distinct().size
    val priorAverage = if (priorDays == 0) 0.0 else priorVisits.count { !it.isTransit() }.toDouble() / priorDays
    val movementDays = visibleHistory.filter { it.isTransit() && it.movementWindow(zoneId) == movementWindow?.key }
        .map { it.localDate(zoneId) }.distinct().size
    val suggestions = buildList {
        if (stopsToName > 0) add(PatternSuggestion.NAME_STOPS)
        if (priorDays >= 3 && todayStops >= priorAverage + 3 && todayStops >= priorAverage * 1.5) {
            add(PatternSuggestion.REVIEW_BUSY_DAY)
        }
        if (recurringPlace != null) add(PatternSuggestion.NOTE_RECURRING_PLACE)
        if (movementDays >= 2 && (movementWindow?.value ?: 0) >= 3) {
            add(PatternSuggestion.REVIEW_AFTER_MOVEMENT)
        }
    }.take(2)

    return TodayPatternAnalysis(
        observedDays = observedDays,
        todayStops = todayStops,
        averageStopsPerDay = if (observedDays == 0) 0.0 else dwellVisits.size.toDouble() / observedDays,
        recurringPlace = recurringPlace?.key,
        recurringPlaceVisits = recurringPlace?.value ?: 0,
        commonMovementWindow = movementWindow?.key,
        commonMovementWindowJourneys = movementWindow?.value ?: 0,
        stopsToName = stopsToName,
        suggestions = suggestions,
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

private fun LocationVisit.patternLabel(): String? =
    (VisitLabels.usable(placeName) ?: VisitLabels.usable(address))
        ?.takeUnless { it.equals("route", ignoreCase = true) || it.startsWith("Near ", ignoreCase = true) }
