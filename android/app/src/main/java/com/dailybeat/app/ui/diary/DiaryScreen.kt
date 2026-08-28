package com.dailybeat.app.ui.diary

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.EventCard
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SectionHeader
import java.time.format.DateTimeFormatter

@Composable
fun DiaryScreen(
    modifier: Modifier = Modifier,
    viewModel: DiaryViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.eventsForDay.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateLabel = uiState.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))

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
                title = stringResource(R.string.diary_screen_title),
                subtitle = dateLabel,
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.generate_section))
            Text(
                text = stringResource(R.string.generate_events_count, uiState.eventCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (uiState.cloudBrainReady) {
                    stringResource(R.string.diary_cloud_hint)
                } else {
                    stringResource(R.string.diary_local_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            PrimaryButton(
                text = if (uiState.cloudBrainReady) {
                    stringResource(R.string.generate_ai_report)
                } else {
                    stringResource(R.string.generate_today_dairy)
                },
                onClick = viewModel::generateFromLoggedEvents,
                enabled = !uiState.isGenerating &&
                    (uiState.eventCount > 0 || uiState.visitCount > 0),
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.custom_events_section))
            OutlinedTextField(
                value = uiState.customEvents,
                onValueChange = viewModel::updateCustomEvents,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.events_input_label)) },
                minLines = 3,
                shape = RoundedCornerShape(16.dp),
                colors = fieldColors,
            )
            SecondaryButton(
                text = stringResource(R.string.generate_from_custom),
                onClick = viewModel::generateFromCustomEvents,
                enabled = !uiState.isGenerating && uiState.customEvents.isNotBlank(),
            )
        }

        if (uiState.isGenerating) {
            item { CircularProgressIndicator() }
        }

        uiState.error?.let { error ->
            item {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }

        if (uiState.text.isNotBlank()) {
            item {
                SectionHeader(title = stringResource(R.string.daily_diary_label))
                OutlinedTextField(
                    value = uiState.text,
                    onValueChange = viewModel::updateDiaryText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.edit_dairy_label)) },
                    minLines = 8,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
            }
            item {
                PrimaryButton(
                    text = stringResource(R.string.share_pdf_button),
                    onClick = {
                        val path = viewModel.exportPdfPath()
                        if (path != null) {
                            val file = java.io.File(path)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Share dairy PDF"))
                        }
                    },
                )
            }
        }

        if (events.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.logged_events_section)) }
            items(events, key = { it.id }) { event ->
                EventCard(event = event)
            }
        }
    }
}
