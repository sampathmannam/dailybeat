package com.dailybeat.app.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(
    onOpenToday: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDay: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("insights_list").padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = stringResource(R.string.insights_title),
                subtitle = stringResource(R.string.insights_subtitle),
            )
        }
        if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

        if (!state.isLoading && state.days.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EmptyState(
                        title = stringResource(R.string.insights_empty_title),
                        subtitle = stringResource(R.string.insights_empty_body),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.open_today),
                        onClick = onOpenToday,
                    )
                }
            }
        } else if (!state.isLoading) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricPill(
                        label = stringResource(R.string.this_week_distance),
                        value = String.format(Locale.US, "%.1f km", state.weeklyDistanceKm),
                        modifier = Modifier.weight(1f),
                    )
                    MetricPill(
                        label = stringResource(R.string.review_streak),
                        value = stringResource(R.string.days_count, state.reviewStreak),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item { WeeklyBars(state.days.take(7)) }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("actionable_insight"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.actionable_insight), style = MaterialTheme.typography.labelMedium)
                        Text(state.insightTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(state.insightBody, style = MaterialTheme.typography.bodyMedium)
                        PrimaryButton(
                            text = when (state.insightAction) {
                                InsightAction.OPEN_SETTINGS -> stringResource(R.string.open_capture_settings)
                                InsightAction.OPEN_DAY -> stringResource(R.string.review_my_day)
                                InsightAction.OPEN_TODAY -> stringResource(R.string.open_today)
                            },
                            onClick = {
                                when (state.insightAction) {
                                    InsightAction.OPEN_SETTINGS -> onOpenSettings()
                                    InsightAction.OPEN_DAY -> state.insightDate?.let { onOpenDay(it.toString()) } ?: onOpenToday()
                                    InsightAction.OPEN_TODAY -> onOpenToday()
                                }
                            },
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.recurring_places), style = MaterialTheme.typography.titleMedium)
                        if (state.recurringPlaces.isEmpty()) {
                            Text(
                                stringResource(R.string.recurring_places_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.recurringPlaces.forEach { pattern ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(pattern.name, Modifier.weight(1f))
                                    Text(
                                        stringResource(R.string.visits_count, pattern.visits),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.insights_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyBars(days: List<com.dailybeat.app.ui.feed.DayFeedItem>) {
    val maximum = days.maxOfOrNull { it.distanceKm }?.takeIf { it > 0 } ?: 1.0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.last_seven_days), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().height(132.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.reversed().forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val height = (88 * (day.distanceKm / maximum)).roundToInt().coerceAtLeast(4)
                        Box(
                            Modifier.width(22.dp).height(height.dp)
                                .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                        )
                        Text(
                            day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1),
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
