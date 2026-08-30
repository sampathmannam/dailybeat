package com.dailybeat.app.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.JourneyMapPreview
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SectionHeader
import com.dailybeat.app.ui.components.VisitCard

@Composable
fun TodayScreen(
    onOpenDiary: () -> Unit,
    modifier: Modifier = Modifier,
    onRecordVoice: (() -> Unit)? = null,
    headerSubtitle: String? = null,
    viewModel: TodayViewModel = viewModel(),
) {
    val visits by viewModel.todayVisits.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var optionalNote by remember { mutableStateOf("") }
    var showOptionalNote by remember { mutableStateOf(false) }
    val showQaTools = booleanResource(R.bool.show_qa_tools)

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

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
            StatusStrip(
                gpsOn = uiState.gpsActive,
                cloudReady = uiState.cloudBrainReady,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricPill(
                    label = stringResource(R.string.stat_visits),
                    value = "${uiState.visitCount}",
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.stat_events),
                    value = "${uiState.eventCount}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SecondaryButton(
                text = stringResource(R.string.mark_significant_moment),
                onClick = viewModel::markSignificantMoment,
            )
        }

        item {
            SecondaryButton(
                text = stringResource(R.string.record_voice_note),
                onClick = onRecordVoice ?: viewModel::recordVoiceNote,
            )
        }

        if (showQaTools) {
            item {
                SecondaryButton(
                    text = stringResource(R.string.load_synthetic_day),
                    onClick = viewModel::seedSyntheticDay,
                    enabled = !uiState.isSeeding,
                )
            }

            uiState.seedMessage?.let { msg ->
                item {
                    Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (uiState.cloudBrainReady) {
            item {
                PrimaryButton(
                    text = stringResource(R.string.generate_ai_report),
                    onClick = viewModel::generateAiReport,
                    enabled = !uiState.isGeneratingReport &&
                        (uiState.visitCount > 0 || uiState.eventCount > 0),
                )
            }
        } else {
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.cloud_brain_setup_hint),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (uiState.isGeneratingReport) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.generating_ai_report))
                }
            }
        }

        if (uiState.hasDiary) {
            item {
                SecondaryButton(
                    text = stringResource(R.string.open_diary_tab),
                    onClick = onOpenDiary,
                )
            }
        }

        uiState.error?.let { error ->
            item { Text(text = error, color = MaterialTheme.colorScheme.error) }
        }

        if (visits.isNotEmpty()) {
            item {
                JourneyMapPreview(visits = visits)
            }
            item { SectionHeader(title = stringResource(R.string.journey_section)) }
            items(visits, key = { it.id }) { visit ->
                VisitCard(visit = visit)
            }
        } else {
            item {
                EmptyState(
                    title = stringResource(R.string.journey_empty_title),
                    subtitle = stringResource(R.string.journey_empty_subtitle),
                )
            }
        }

        if (uiState.isRecordingVoice) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.recording_label))
                }
            }
        }

        uiState.voiceMessage?.let { msg ->
            item {
                Text(text = msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        item {
            SecondaryButton(
                text = if (showOptionalNote) {
                    stringResource(R.string.hide_optional_note)
                } else {
                    stringResource(R.string.add_optional_note)
                },
                onClick = { showOptionalNote = !showOptionalNote },
            )
        }

        if (showOptionalNote) {
            item {
                OutlinedTextField(
                    value = optionalNote,
                    onValueChange = { optionalNote = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.optional_note_label)) },
                    minLines = 2,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
                PrimaryButton(
                    text = stringResource(R.string.save_optional_note),
                    onClick = {
                        viewModel.addOptionalNote(optionalNote)
                        optionalNote = ""
                        showOptionalNote = false
                    },
                    enabled = optionalNote.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(gpsOn: Boolean, cloudReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusChip(
            label = if (gpsOn) stringResource(R.string.status_gps_on) else stringResource(R.string.status_gps_off),
            active = gpsOn,
        )
        StatusChip(
            label = if (cloudReady) stringResource(R.string.status_cloud_on) else stringResource(R.string.status_cloud_off),
            active = cloudReady,
        )
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active && label.contains("Cloud")) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(0.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
