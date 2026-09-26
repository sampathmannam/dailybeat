package com.dailybeat.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import java.text.NumberFormat

@Composable
internal fun TodayPatternDashboard(
    analysis: TodayPatternAnalysis,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("today_pattern_dashboard"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.today_pattern_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.today_pattern_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.today_pattern_on_device),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (!analysis.hasUsefulHistory) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = stringResource(R.string.today_pattern_learning_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.today_pattern_learning_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val number = NumberFormat.getNumberInstance().apply {
                    maximumFractionDigits = 1
                    minimumFractionDigits = 0
                }
                PatternSignal(
                    label = stringResource(R.string.today_pattern_rhythm_label),
                    value = pluralStringResource(
                        R.plurals.today_pattern_stops,
                        analysis.todayStops,
                        analysis.todayStops,
                    ),
                    detail = stringResource(
                        R.string.today_pattern_rhythm_detail,
                        number.format(analysis.averageStopsPerDay),
                        analysis.observedDays,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PatternSignal(
                    label = stringResource(R.string.today_pattern_place_label),
                    value = analysis.recurringPlace
                        ?: stringResource(R.string.today_pattern_place_learning),
                    detail = if (analysis.recurringPlace != null) {
                        pluralStringResource(
                            R.plurals.today_pattern_place_visits,
                            analysis.recurringPlaceVisits,
                            analysis.recurringPlaceVisits,
                        )
                    } else {
                        stringResource(R.string.today_pattern_place_learning_detail)
                    },
                )
                analysis.commonMovementWindow?.let { window ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PatternSignal(
                        label = stringResource(R.string.today_pattern_window_label),
                        value = stringResource(window.stringResource()),
                        detail = pluralStringResource(
                            R.plurals.today_pattern_window_journeys,
                            analysis.commonMovementWindowJourneys,
                            analysis.commonMovementWindowJourneys,
                        ),
                    )
                }
            }

            if (analysis.suggestions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier.testTag("today_pattern_suggestions"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.today_pattern_suggestions_title),
                        style = MaterialTheme.typography.titleMedium)
                    analysis.suggestions.forEach { suggestion ->
                        Text(
                            text = when (suggestion) {
                                PatternSuggestion.NAME_STOPS -> pluralStringResource(
                                    R.plurals.today_pattern_suggest_names, analysis.stopsToName, analysis.stopsToName)
                                PatternSuggestion.NOTE_RECURRING_PLACE -> stringResource(
                                    R.string.today_pattern_suggest_note, analysis.recurringPlace.orEmpty())
                                PatternSuggestion.REVIEW_AFTER_MOVEMENT -> stringResource(
                                    R.string.today_pattern_suggest_review,
                                    stringResource(requireNotNull(analysis.commonMovementWindow).stringResource()))
                                PatternSuggestion.REVIEW_BUSY_DAY -> stringResource(R.string.today_pattern_suggest_busy)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 1.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.today_pattern_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatternSignal(label: String, value: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun MovementWindow.stringResource(): Int = when (this) {
    MovementWindow.MORNING -> R.string.today_pattern_window_morning
    MovementWindow.AFTERNOON -> R.string.today_pattern_window_afternoon
    MovementWindow.EVENING -> R.string.today_pattern_window_evening
    MovementWindow.NIGHT -> R.string.today_pattern_window_night
}
