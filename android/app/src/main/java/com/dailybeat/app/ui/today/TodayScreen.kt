package com.dailybeat.app.ui.today

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.JourneyRoutePreview
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import kotlin.math.roundToInt
import com.dailybeat.app.util.Formatters

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
    val beat by viewModel.todayBeat.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var note by rememberSaveable { mutableStateOf("") }
    var showCaptureSheet by rememberSaveable { mutableStateOf(false) }
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
        ModalBottomSheet(onDismissRequest = { showCaptureSheet = false }) {
            CaptureMomentSheet(
                note = note,
                onNoteChanged = { note = it },
                uiState = uiState,
                onMarkMoment = {
                    viewModel.markSignificantMoment()
                    showCaptureSheet = false
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
                WaitingForRouteCard(uiState.captureStatus)
            }
        }

        item { CaptureHealthCard(uiState.captureStatus) }

        item { StatusStrip(gpsOn = uiState.gpsActive, cloudReady = uiState.cloudBrainReady) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = beat.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (uiState.beatState == "complete") {
                        stringResource(R.string.beat_complete_status)
                    } else {
                        stringResource(R.string.beat_live_status)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(
                    label = stringResource(R.string.feed_stat_distance),
                    value = Formatters.distance(uiState.distanceMeters, uiState.distanceEstimated),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.beat_tracked_time),
                    value = Formatters.durationCompact(uiState.trackedMinutes),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.feed_stat_stops),
                    value = Formatters.count(visits.count { it.visitType != "transit" }),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (uiState.distanceEstimated) {
            item {
                Text(
                    stringResource(R.string.distance_estimated_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun WaitingForRouteCard(status: CaptureHealthStatus) {
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
                text = if (status.level == CaptureHealthLevel.OFF) {
                    stringResource(R.string.map_tracking_off_body)
                } else {
                    stringResource(R.string.map_waiting_body)
                },
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CaptureHealthCard(status: CaptureHealthStatus) {
    val (title, detail) = when (status.level) {
        CaptureHealthLevel.HEALTHY -> stringResource(R.string.capture_healthy) to
            status.accuracyM?.let { stringResource(R.string.capture_accuracy, it.roundToInt()) }
        CaptureHealthLevel.WAITING -> stringResource(R.string.capture_waiting) to
            stringResource(R.string.capture_waiting_detail)
        CaptureHealthLevel.DEGRADED -> stringResource(R.string.capture_degraded) to
            stringResource(R.string.capture_degraded_detail)
        CaptureHealthLevel.CRITICAL -> stringResource(R.string.capture_critical) to
            stringResource(R.string.capture_critical_detail)
        CaptureHealthLevel.OFF -> stringResource(R.string.capture_off) to
            stringResource(R.string.capture_off_detail)
    }
    val color = when (status.level) {
        CaptureHealthLevel.HEALTHY -> MaterialTheme.colorScheme.primary
        CaptureHealthLevel.WAITING, CaptureHealthLevel.DEGRADED -> MaterialTheme.colorScheme.tertiary
        CaptureHealthLevel.CRITICAL, CaptureHealthLevel.OFF -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("capture_health"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = color) {}
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = detail ?: stringResource(R.string.capture_healthy_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        PrimaryButton(text = stringResource(R.string.mark_significant_moment), onClick = onMarkMoment)
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
            text = stringResource(R.string.save_optional_note),
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
private fun StatusStrip(gpsOn: Boolean, cloudReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(
            label = if (gpsOn) stringResource(R.string.status_gps_on) else stringResource(R.string.status_gps_off),
            active = gpsOn,
            icon = Icons.Default.MyLocation,
            testTag = "status_gps",
            modifier = Modifier.weight(1f),
        )
        StatusChip(
            label = if (cloudReady) stringResource(R.string.status_cloud_on) else stringResource(R.string.status_cloud_off),
            active = cloudReady,
            icon = Icons.Default.AutoAwesome,
            testTag = "status_cloud",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 40.dp).testTag(testTag),
        shape = MaterialTheme.shapes.small,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedbackMessage(message: String, error: Boolean, onDismiss: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            onDismiss?.let { dismiss ->
                TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

