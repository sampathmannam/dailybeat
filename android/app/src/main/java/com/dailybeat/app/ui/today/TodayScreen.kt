package com.dailybeat.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.capture.CaptureHealthLevel
import com.dailybeat.app.capture.CaptureHealthStatus
import com.dailybeat.app.ui.components.EventCard
import com.dailybeat.app.ui.components.InlineFeedback
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.JourneyRoutePreview
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.feed.DayStay
import com.dailybeat.app.util.Formatters
import com.dailybeat.app.util.InputPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenDiary: () -> Unit,
    onOpenMap: () -> Unit,
    onReviewDay: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordVoice: (() -> Unit)? = null,
    headerSubtitle: String? = null,
    viewModel: TodayViewModel = viewModel(),
) {
    val visits by viewModel.todayVisits.collectAsStateWithLifecycle()
    val events by viewModel.todayEvents.collectAsStateWithLifecycle()
    val beat by viewModel.todayBeat.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var showCaptureSheet by rememberSaveable { mutableStateOf(false) }
    // Place names are deliberately collapsed whenever Today is opened. Saving this expansion
    // across process recreation made a previously opened list look like permanent map content.
    var showRouteDetails by remember(beat.date) { mutableStateOf(false) }
    val showQaTools = booleanResource(R.bool.show_qa_tools)
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshStatus()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (showCaptureSheet) {
        val captureBusy = uiState.isSavingMoment || uiState.isSavingNote ||
            uiState.isRecordingVoice || uiState.isGeneratingReport
        ModalBottomSheet(onDismissRequest = { if (!captureBusy) showCaptureSheet = false }) {
            CaptureMomentSheet(
                note = note,
                onNoteChanged = {
                    note = InputPolicy.multiline(it, InputPolicy.MOMENT_NOTE_CHARS)
                },
                uiState = uiState,
                onMarkMoment = {
                    viewModel.markSignificantMoment { showCaptureSheet = false }
                },
                onRecordVoice = {
                    (onRecordVoice ?: viewModel::recordVoiceNote).invoke()
                    showCaptureSheet = false
                },
                onSaveNote = {
                    viewModel.addOptionalNote(note) {
                        note = ""
                        showCaptureSheet = false
                    }
                },
                onGenerateReport = {
                    viewModel.generateAiReport()
                    showCaptureSheet = false
                },
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .readableContentWidth()
            .testTag("today_list")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = stringResource(R.string.today_passive_title),
                subtitle = headerSubtitle ?: stringResource(R.string.today_passive_subtitle),
            )
        }

        item {
            if (beat.hasRoute) {
                JourneyRoutePreview(route = beat.route, onOpenMap = onOpenMap)
            } else {
                WaitingForRouteCard(
                    status = uiState.captureStatus,
                    locationPermissionGranted = uiState.locationPermissionGranted,
                )
            }
        }

        item {
            TodaySummaryCard(
                distanceLabel = stringResource(R.string.feed_stat_distance),
                distanceValue = Formatters.distance(uiState.distanceMeters, uiState.distanceEstimated),
                timeLabel = stringResource(R.string.beat_tracked_time),
                timeValue = Formatters.durationCompact(uiState.trackedMinutes),
                stopsLabel = stringResource(R.string.feed_stat_auto_stops),
                stopsValue = Formatters.count(beat.stays.size),
                stays = beat.stays,
                expanded = showRouteDetails,
                onToggleDetails = { showRouteDetails = !showRouteDetails },
            )
        }

        item {
            PrimaryButton(
                text = if (uiState.beatState == "complete") {
                    stringResource(R.string.view_completed_beat)
                } else {
                    stringResource(R.string.review_my_day)
                },
                onClick = onReviewDay,
                modifier = Modifier.testTag("review_day"),
                enabled = beat.hasRoute || visits.isNotEmpty() || uiState.eventCount > 0,
            )
        }

        item {
            SecondaryButton(
                text = stringResource(R.string.add_moment),
                onClick = { showCaptureSheet = true },
                modifier = Modifier.testTag("add_moment"),
            )
        }

        item {
            TextButton(onClick = onOpenDiary, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (uiState.hasDiary) stringResource(R.string.open_diary_tab)
                    else stringResource(R.string.add_diary),
                )
            }
        }

        item {
            TodayMomentsHeader()
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.today_moments_empty),
                    modifier = Modifier.testTag("today_moments_empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(todayMomentsOrder(events), key = { it.id }) { event ->
                EventCard(event = event)
            }
        }

        if (showQaTools) {
            item {
                SecondaryButton(
                    text = stringResource(R.string.load_synthetic_day),
                    onClick = viewModel::seedSyntheticDay,
                    enabled = !uiState.isSeeding,
                )
            }
        }

        uiState.seedMessage?.let { message -> item { FeedbackMessage(message, false) } }
        uiState.successMessage?.let { message ->
            item { FeedbackMessage(message, false) { viewModel.clearMessage() } }
        }
        uiState.error?.let { error -> item { FeedbackMessage(error, true) } }

        if (uiState.isGeneratingReport || uiState.isRecordingVoice) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = if (uiState.isGeneratingReport) {
                            stringResource(R.string.generating_ai_report)
                        } else {
                            stringResource(R.string.recording_label)
                        },
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

/**
 * "Today's moments · Local to this phone". The provenance half is not decoration: this list is the
 * one place the officer sees raw personal notes, and the app's whole promise is that they stay on
 * the device unless a deliberate backup, AI, export or share action says otherwise.
 */
@Composable
private fun TodayMomentsHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.today_moments_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.today_moments_provenance),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WaitingForRouteCard(
    status: CaptureHealthStatus,
    locationPermissionGranted: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 188.dp).testTag("today_map_empty"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondary,
                )
            }
            Text(
                text = stringResource(R.string.map_waiting_title),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (status.level) {
                    CaptureHealthLevel.OFF -> if (locationPermissionGranted) {
                        stringResource(R.string.map_tracking_off_body)
                    } else {
                        stringResource(R.string.map_location_permission_body)
                    }
                    CaptureHealthLevel.PAUSED -> stringResource(R.string.map_paused_body)
                    CaptureHealthLevel.WATCHING -> stringResource(R.string.map_watching_body)
                    else -> stringResource(R.string.map_waiting_body)
                },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodaySummaryCard(
    distanceLabel: String,
    distanceValue: String,
    timeLabel: String,
    timeValue: String,
    stopsLabel: String,
    stopsValue: String,
    stays: List<DayStay>,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("today_summary"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryMetric(distanceValue, distanceLabel, Modifier.weight(1f))
                SummaryMetric(timeValue, timeLabel, Modifier.weight(1f))
                SummaryMetric(stopsValue, stopsLabel, Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onToggleDetails,
                    modifier = Modifier.testTag("today_more"),
                ) {
                    Text(stringResource(if (expanded) R.string.today_less else R.string.today_more))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (expanded) {
                RouteDetails(stays)
            }
        }
    }
}

@Composable
private fun RouteDetails(stays: List<DayStay>) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("today_route_details"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.today_where_you_went),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (stays.isEmpty()) {
            Text(
                text = stringResource(R.string.today_no_auto_stops),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            stays.forEachIndexed { index, stay ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stay.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.today_stop_time,
                            Formatters.clock(stay.startMs),
                            Formatters.clock(stay.endMs),
                            Formatters.duration(stay.durationMinutes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaptureMomentSheet(
    note: String,
    onNoteChanged: (String) -> Unit,
    uiState: TodayUiState,
    onMarkMoment: () -> Unit,
    onRecordVoice: () -> Unit,
    onSaveNote: () -> Unit,
    onGenerateReport: () -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.add_to_today), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.add_to_today_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(
            text = if (uiState.isSavingMoment) {
                stringResource(R.string.saving_moment)
            } else {
                stringResource(R.string.mark_significant_moment)
            },
            onClick = onMarkMoment,
            enabled = !uiState.isSavingMoment,
        )
        SecondaryButton(
            text = stringResource(R.string.record_voice_note),
            onClick = onRecordVoice,
            enabled = !uiState.isRecordingVoice,
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChanged,
            modifier = Modifier.fillMaxWidth().testTag("moment_note"),
            label = { Text(stringResource(R.string.optional_note_label)) },
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
        )
        SecondaryButton(
            text = if (uiState.isSavingNote) {
                stringResource(R.string.saving_note)
            } else {
                stringResource(R.string.save_optional_note)
            },
            onClick = onSaveNote,
            enabled = note.isNotBlank() && !uiState.isSavingNote,
        )
        if (uiState.cloudBrainReady) {
            TextButton(
                onClick = onGenerateReport,
                enabled = !uiState.isGeneratingReport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Text(stringResource(R.string.generate_ai_report), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun FeedbackMessage(message: String, error: Boolean, onDismiss: (() -> Unit)? = null) {
    InlineFeedback(
        message = message,
        isError = error,
        actionLabel = if (onDismiss != null) stringResource(R.string.dismiss) else null,
        onAction = onDismiss,
    )
}
