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
import com.dailybeat.app.domain.VisitLabels
import com.dailybeat.app.ui.feed.DayStay
import com.dailybeat.app.ui.components.CaptureCoverageNote
import com.dailybeat.app.ui.components.JourneyRoutePreview
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.util.Formatters
import com.dailybeat.app.util.InputPolicy
import androidx.compose.foundation.layout.imePadding

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
    var renameVisit by remember(state.dataGeneration) { mutableStateOf<LocationVisit?>(null) }

    renameVisit?.let { visit ->
        RenameStopDialog(
            visit = visit,
            currentLabel = reviewVisitLabel(visit, day.stays),
            onDismiss = { renameVisit = null },
            onSave = { name ->
                viewModel.renameVisit(visit, name)
                renameVisit = null
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().readableContentWidth().imePadding().testTag("review_day_screen"),
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
                        Formatters.dayHeading(viewModel.date),
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
                    value = Formatters.distanceKm(day.distanceKm, day.distanceEstimated),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.feed_stat_stops),
                    value = Formatters.count(day.stayCount),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.review_events),
                    value = Formatters.count(state.eventCount),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            CaptureCoverageNote(
                gapCount = day.captureGapCount,
                hasCapture = day.hasRoute || visits.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
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
                displayName = reviewVisitLabel(visit, day.stays),
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

@Composable
private fun ReviewVisitRow(
    visit: LocationVisit,
    displayName: String,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Surface(
                shape = CircleShape,
                color = if (visit.hidden) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.secondary,
            ) {
                androidx.compose.foundation.layout.Box(Modifier.size(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (visit.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    Formatters.clockRange(visit.startMs, visit.endMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // A hidden stop used to differ from a visible one by a 1.12:1 background tint and
                // nothing else. Say it.
                if (visit.hidden) {
                    Text(
                        stringResource(R.string.hidden_stop_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (visit.manuallyEdited) {
                    Text(
                        stringResource(R.string.visit_correction_retained),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row {
                    TextButton(
                        onClick = onRename,
                        modifier = Modifier.testTag("rename_visit_${visit.id}"),
                    ) {
                        Text(stringResource(R.string.rename_stop))
                    }
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
private fun RenameStopDialog(
    visit: LocationVisit,
    currentLabel: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(visit.id) { mutableStateOf(reviewRenameSeed(currentLabel)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_stop)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = InputPolicy.singleLine(it, InputPolicy.PLACE_NAME_CHARS)
                },
                label = { Text(stringResource(R.string.place_name_label)) },
                modifier = Modifier.testTag("rename_stop_name"),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("rename_stop_save"),
            ) {
                Text(stringResource(R.string.save_title))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The already-built day includes current saved-place labels; hidden rows stay reviewable. */
internal fun reviewVisitLabel(visit: LocationVisit, stays: List<DayStay>): String =
    (if (!visit.hidden && visit.id > 0) stays.firstOrNull { it.visitId == visit.id }?.name else null)
        ?: VisitLabels.displayName(visit, shortAddress = true)

internal fun reviewRenameSeed(label: String): String = label.takeUnless(VisitLabels::isFallback).orEmpty()

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
