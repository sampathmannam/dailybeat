package com.dailybeat.app.ui.insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.ui.feed.DayFeedBuilder
import com.dailybeat.app.ui.feed.DayFeedItem
import com.dailybeat.app.util.DateKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import com.dailybeat.app.util.userMessage

data class PlacePattern(val name: String, val visits: Int)

data class InsightsUiState(
    val isLoading: Boolean = true,
    val days: List<DayFeedItem> = emptyList(),
    val weekDays: List<DayFeedItem> = emptyList(),
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
) {
    // Empty/future days default to estimated but do not contribute evidence to this total.
    val weeklyDistanceEstimated: Boolean
        get() = weekDays.any { !it.isEmpty && it.distanceEstimated }
}

enum class InsightAction { OPEN_TODAY, OPEN_SETTINGS, OPEN_DAY }

class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DailyBeatApp
    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var observedDataGeneration = CaptureStorageGate.dataGeneration.get()

    init {
        refresh()
        viewModelScope.launch {
            CaptureStorageGate.dataChanges.collect {
                val generation = CaptureStorageGate.dataGeneration.get()
                if (generation != observedDataGeneration) {
                    observedDataGeneration = generation
                    refreshJob?.cancel()
                    _uiState.value = InsightsUiState()
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        val generation = CaptureStorageGate.dataGeneration.get()
        refreshJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { load() } }.fold(
                onSuccess = {
                    if (generation == CaptureStorageGate.dataGeneration.get()) _uiState.value = it
                },
                onFailure = { cause ->
                    if (cause is CancellationException) throw cause
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = cause.userMessage("Unable to calculate insights."),
                    )
                },
            )
        }
    }

    private suspend fun load(): InsightsUiState {
        val today = DateKeys.today()
        val places = app.placeRepository.all()
        val reviews = app.beatRepository.all().associateBy { it.dateKey }
        val historyDates = (0 until 28).map { today.minusDays(it.toLong()) }
        val weekDates = mondayToSundayWeek(today)
        val daysByDate = (historyDates + weekDates).distinct().associateWith { date ->
            DayFeedBuilder.build(
                date = date,
                visits = app.visitRepository.visitsForDate(date),
                diaryText = app.diaryRepository.textForDate(date),
                places = places,
                breadcrumbs = app.breadcrumbRepository.forDate(date),
                review = reviews[date.toString()],
            )
        }
        val days = historyDates.map(daysByDate::getValue)
        val activeDays = days.filterNot { it.isEmpty }
        val currentWeek = weekDates.map(daysByDate::getValue)
        val reviewedDays = activeDays.count { it.state == "complete" }
        val nextReviewDay = activeDays.firstOrNull { it.state != "complete" }
        val streak = reviewStreak(today, reviews.mapValues { it.value.state })
        val gaps = currentWeek.sumOf { it.captureGapCount }
        val patterns = activeDays.flatMap { it.stays }
            .filterNot { VisitLabels.isFallback(it.name) }
            .groupingBy { it.name }
            .eachCount()
            .map { PlacePattern(it.key, it.value) }
            .filter { it.visits > 1 }
            .sortedByDescending { it.visits }
            .take(4)
        val insight = actionableInsightFor(today, nextReviewDay, reviewedDays)
        return InsightsUiState(
            isLoading = false,
            days = activeDays,
            weekDays = currentWeek,
            reviewedDays = reviewedDays,
            reviewStreak = streak,
            weeklyDistanceKm = currentWeek.sumOf { it.distanceKm },
            weeklyTrackedMinutes = currentWeek.sumOf { it.trackedMinutes },
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

/** A calendar week is always Monday through Sunday, independent of locale and today's weekday. */
internal fun mondayToSundayWeek(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map(monday::plusDays)
}

internal data class ActionableInsight(
    val title: String,
    val body: String,
    val action: InsightAction,
    val date: LocalDate? = null,
)

internal fun actionableInsightFor(
    today: LocalDate,
    nextReviewDay: DayFeedItem?,
    reviewedDays: Int,
): ActionableInsight = when {
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
