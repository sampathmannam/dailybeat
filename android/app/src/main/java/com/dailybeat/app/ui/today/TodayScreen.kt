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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.EventCard
import com.dailybeat.app.ui.components.MetricPill
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SectionHeader

@Composable
fun TodayScreen(
    onOpenDiary: () -> Unit,
    headerSubtitle: String? = null,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(),
) {
    val events by viewModel.todayEvents.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            DailyBeatScreenHeader(
                title = stringResource(R.string.today_dashboard_title),
                subtitle = headerSubtitle ?: stringResource(R.string.today_dashboard_subtitle),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricPill(
                    label = stringResource(R.string.stat_events),
                    value = uiState.eventCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                MetricPill(
                    label = stringResource(R.string.stat_diary),
                    value = if (uiState.hasDiary) "✓" else "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.quick_add_section))
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_event_label)) },
                minLines = 2,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
            )
        }

        item {
            PrimaryButton(
                text = stringResource(R.string.save_event),
                onClick = {
                    viewModel.addManualEvent(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            )
        }

        if (events.isNotEmpty()) {
            item {
                SecondaryButton(
                    text = stringResource(R.string.open_diary_tab),
                    onClick = onOpenDiary,
                )
            }
        }

        if (isRecording) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(
                        text = stringResource(R.string.recording_label),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        uiState.error?.let { error ->
            item {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }

        if (!uiState.modelAvailable) {
            item {
                Text(
                    text = stringResource(R.string.model_missing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (events.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.no_events_today),
                    subtitle = stringResource(R.string.no_events_hint),
                )
            }
        } else {
            item { SectionHeader(title = stringResource(R.string.event_list_label)) }
            items(events, key = { it.id }) { event ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    EventCard(event = event, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteEvent(event) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_event),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
