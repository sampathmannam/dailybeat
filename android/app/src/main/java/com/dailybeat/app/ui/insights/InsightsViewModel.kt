package com.dailybeat.app.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PlacePattern(val name: String, val visits: Int)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val days: List<DayFeedItem> = emptyList(),
    val reviewedDays: Int = 0,
    val reviewStreak: Int = 0,
    val weeklyDistanceKm: Double = 0.0,
    val weeklyTrackedMinutes: Long = 0,
    val captureGapCount: Int = 0,
    val recurringPlaces: List<PlacePattern> = emptyList(),
    val insightTitle: String = "Build your first Beat",
    val insightBody: String = "Keep capture on today, then review the route this evening.",
    val insightAction: InsightAction = InsightAction.OPEN_TODAY,
    val insightDate: LocalDate? = null,
    val error: String? = null,
)

enum class InsightAction { OPEN_TODAY, OPEN_SETTINGS, OPEN_DAY }

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { load() } }.fold(
                onSuccess = { _uiState.value = it },
                onFailure = { cause ->
                    if (cause is CancellationException) throw cause
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = cause.message ?: "Unable to calculate insights.",
                    )
                },
            )
        }
    }

    private suspend fun load(): InsightsUiState {
        val today = DateKeys.today()
        val places = app.placeRepository.all()
        val reviews = app.beatRepository.all().associateBy { it.dateKey }
        val days = (0 until 28).map { today.minusDays(it.toLong()) }.map { date ->
            DayFeedBuilder.build(
                date = date,
                visits = app.visitRepository.visitsForDate(date),
                diaryText = app.diaryRepository.textForDate(date),
                places = places,
                breadcrumbs = app.breadcrumbRepository.forDate(date),
                review = reviews[date.toString()],
            )
        }
        val activeDays = days.filterNot { it.isEmpty }
        val recentWeek = days.take(7)
        val reviewedDays = activeDays.count { it.state == "complete" }
        val nextReviewDay = activeDays.firstOrNull { it.state != "complete" }
        val streak = reviewStreak(today, reviews.mapValues { it.value.state })
        val gaps = recentWeek.sumOf { it.captureGapCount }
        val patterns = activeDays.flatMap { it.stays }
            .groupingBy { it.name }
            .eachCount()
            .map { PlacePattern(it.key, it.value) }
            .filter { it.visits > 1 }
            .sortedByDescending { it.visits }
            .take(4)
        val insight = actionableInsightFor(today, gaps, nextReviewDay, reviewedDays)
        return InsightsUiState(
            isLoading = false,
            days = activeDays,
            reviewedDays = reviewedDays,
            reviewStreak = streak,
            weeklyDistanceKm = recentWeek.sumOf { it.distanceKm },
            weeklyTrackedMinutes = recentWeek.sumOf { it.trackedMinutes },
            captureGapCount = gaps,
            recurringPlaces = patterns,
            insightTitle = insight.title,
            insightBody = insight.body,
            insightAction = insight.action,
            insightDate = insight.date,
        )
    }

    private fun reviewStreak(today: LocalDate, states: Map<String, String>): Int {
        var date = today
        var result = 0
        while (states[date.toString()] == "complete") {
            result++
            date = date.minusDays(1)
        }
        return result
    }
}

internal data class ActionableInsight(
    val title: String,
    val body: String,
    val action: InsightAction,
    val date: LocalDate? = null,
)

internal fun actionableInsightFor(
    today: LocalDate,
    captureGapCount: Int,
    nextReviewDay: DayFeedItem?,
    reviewedDays: Int,
): ActionableInsight = when {
    captureGapCount > 0 -> ActionableInsight(
        "Protect route continuity",
        "$captureGapCount capture gap(s) appeared this week. Set battery use to Unrestricted and confirm Precise location before tomorrow’s rounds.",
        InsightAction.OPEN_SETTINGS,
    )
    nextReviewDay?.date == today -> ActionableInsight(
        title = "Close today’s loop",
        body = "Review today’s stops while they are fresh. Completing a Beat turns raw tracking into a record you can trust.",
        action = InsightAction.OPEN_DAY,
        date = nextReviewDay.date,
    )
    nextReviewDay != null -> ActionableInsight(
        title = "Review your latest open Beat",
        body = "${nextReviewDay.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} still needs review. Confirm the stops now so your history stays trustworthy.",
        action = InsightAction.OPEN_DAY,
        date = nextReviewDay.date,
    )
    reviewedDays == 0 -> ActionableInsight(
        "Build your first Beat",
        "Keep capture on today, then review the route this evening.",
        InsightAction.OPEN_TODAY,
    )
    else -> ActionableInsight(
        "Keep the review habit",
        "You have completed $reviewedDays ${if (reviewedDays == 1) "active day" else "active days"} in the last 28 days. Keep capture on and review today’s Beat this evening.",
        InsightAction.OPEN_TODAY,
    )
}
