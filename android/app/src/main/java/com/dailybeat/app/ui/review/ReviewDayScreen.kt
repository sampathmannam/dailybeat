package com.dailybeat.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.components.JourneyRoutePreview
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ReviewDayScreen(
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenDiary: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewDayViewModel = viewModel(),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val visits by viewModel.visits.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renameVisit by remember { mutableStateOf<LocationVisit?>(null) }

    renameVisit?.let { visit ->
        RenameStopDialog(
            visit = visit,
            onDismiss = { renameVisit = null },
            onSave = { name ->
                viewModel.renameVisit(visit, name)
                renameVisit = null
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("review_day_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("review_day_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.review_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_day_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        viewModel.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StateBadge(day.state)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    modifier = Modifier.fillMaxWidth().testTag("beat_title"),
                    label = { Text(stringResource(R.string.beat_title_label)) },
                    singleLine = true,
                )
                TextButton(onClick = viewModel::saveTitle, enabled = !state.isSaving) {
                    Text(stringResource(R.string.save_title))
                }
            }
        }

        if (day.hasRoute) {
            item {
                JourneyRoutePreview(
                    route = day.route,
                    onOpenMap = onOpenMap,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(
                    label = stringResource(R.string.feed_stat_distance),
                    value = reviewDistanceLabel(day.distanceKm, day.distanceEstimated),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.feed_stat_stops),
                    value = day.stayCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.notes),
                    value = state.eventCount.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (day.captureGapCount > 0) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                ) {
                    Text(
                        stringResource(R.string.review_gap_detail, day.captureGapCount),
                        Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
        }

        item {
            Text(
                stringResource(R.string.review_timeline),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        items(visits, key = { it.id }) { visit ->
            ReviewVisitRow(
                visit = visit,
                onRename = { renameVisit = visit },
                onToggleHidden = { viewModel.setVisitHidden(visit, !visit.hidden) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (visits.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.review_no_stops),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    text = if (state.diaryText.isNullOrBlank()) stringResource(R.string.add_diary)
                    else stringResource(R.string.open_diary_tab),
                    onClick = { onOpenDiary(viewModel.date.toString()) },
                )
                if (day.state == "complete") {
                    SecondaryButton(
                        text = stringResource(R.string.reopen_beat),
                        onClick = viewModel::reopen,
                        enabled = !state.isSaving,
                    )
                } else {
                    PrimaryButton(
                        text = stringResource(R.string.complete_beat),
                        onClick = viewModel::complete,
                        enabled = !state.isSaving,
                        modifier = Modifier.testTag("complete_beat"),
                    )
                }
            }
        }

        state.message?.let { message ->
            item { ReviewFeedback(message, false, viewModel::clearMessage) }
        }
        state.error?.let { error ->
            item { ReviewFeedback(error, true, viewModel::clearMessage) }
        }
    }
}

private fun reviewDistanceLabel(kilometers: Double, estimated: Boolean): String {
    val roundedTenth = (kilometers * 10).roundToInt() / 10.0
    val value = if (roundedTenth % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f km", roundedTenth)
    } else {
        String.format(Locale.US, "%.1f km", roundedTenth)
    }
    return if (estimated && kilometers > 0) "~$value" else value
}

@Composable
private fun ReviewVisitRow(
    visit: LocationVisit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    Surface(
        modifier = modifier.fillMaxWidth().testTag("review_visit_${visit.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (visit.hidden) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (visit.hidden) 0.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary) {
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    visit.placeName?.takeIf { it.isNotBlank() }
                        ?: visit.address?.substringBefore(',')?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.unnamed_stop),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (visit.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${formatter.format(Instant.ofEpochMilli(visit.startMs))} – ${formatter.format(Instant.ofEpochMilli(visit.endMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = onRename) { Text(stringResource(R.string.rename_stop)) }
                    TextButton(
                        onClick = onToggleHidden,
                        modifier = Modifier.testTag("hide_visit_${visit.id}"),
                    ) {
                        Text(if (visit.hidden) stringResource(R.string.restore_stop) else stringResource(R.string.hide_stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameStopDialog(visit: LocationVisit, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember(visit.id) { mutableStateOf(visit.placeName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_stop)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(120) },
                label = { Text(stringResource(R.string.place_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save_title))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun StateBadge(state: String) {
    Surface(
        shape = CircleShape,
        color = if (state == "complete") MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Text(
            if (state == "complete") stringResource(R.string.complete_label) else stringResource(R.string.needs_review_label),
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ReviewFeedback(message: String, error: Boolean, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
        }
    }
}
