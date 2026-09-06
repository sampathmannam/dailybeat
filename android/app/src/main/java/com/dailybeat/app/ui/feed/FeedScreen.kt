package com.dailybeat.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.util.DateKeys
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayHeadingFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

@Composable
fun FeedScreen(
    onOpenDiary: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("feed_list")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = stringResource(R.string.feed_title),
                subtitle = stringResource(R.string.feed_subtitle),
            )
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.generate_weekly_rollup),
                onClick = viewModel::generateWeeklyRollup,
                enabled = !state.isGeneratingWeekly,
            )
            SecondaryButton(
                text = stringResource(R.string.export_week_package),
                onClick = viewModel::exportPackage,
                enabled = !state.isExporting,
            )
            if (state.isGeneratingWeekly || state.isExporting) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
            }
            state.message?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (state.days.isEmpty() && !state.isLoading) {
            item {
                EmptyState(
                    title = stringResource(R.string.feed_empty_title),
                    subtitle = stringResource(R.string.feed_empty_subtitle),
                )
            }
        }

        items(state.days, key = { it.date.toString() }) { day ->
            DayFeedCard(day = day, onClick = { onOpenDiary(DateKeys.format(day.date)) })
        }
    }
}

@Composable
private fun DayFeedCard(day: DayFeedItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feed_card_${day.date}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = relativeDayLabel(day.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = day.date.format(dayHeadingFormat),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (day.hasRoute) {
                DayRouteThumbnail(
                    route = day.route,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentDescription = stringResource(
                        R.string.feed_route_content_description,
                        day.stayCount,
                    ),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatBlock(
                    value = formatDistance(day.distanceKm),
                    label = stringResource(R.string.feed_stat_distance),
                )
                StatBlock(
                    value = formatDuration(day.activeMinutes),
                    label = stringResource(R.string.feed_stat_time_out),
                )
                StatBlock(
                    value = day.stayCount.toString(),
                    label = stringResource(R.string.feed_stat_stops),
                )
            }

            if (day.stays.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    day.stays.take(MAX_STAYS_SHOWN).forEach { StayRow(it) }
                    if (day.stays.size > MAX_STAYS_SHOWN) {
                        Text(
                            text = stringResource(
                                R.string.feed_more_stops,
                                day.stays.size - MAX_STAYS_SHOWN,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            day.diaryPreview?.let { preview ->
                Text(
                    text = preview,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StayRow(stay: DayStay) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stay.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatClock(stay.startMs)} · ${formatDuration(stay.durationMinutes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatBlock(value: String, label: String) {
    Column {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val MAX_STAYS_SHOWN = 5

internal fun formatDistance(km: Double): String = when {
    km < 0.1 -> "—"
    km < 1.0 -> String.format(Locale.getDefault(), "%d m", (km * 1000).toInt())
    else -> String.format(Locale.getDefault(), "%.1f km", km)
}

internal fun formatDuration(minutes: Long): String = when {
    minutes <= 0 -> "—"
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

private fun formatClock(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(clockFormat)

internal fun relativeDayLabel(date: LocalDate, today: LocalDate = DateKeys.today()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> "${java.time.temporal.ChronoUnit.DAYS.between(date, today)} days ago"
}
