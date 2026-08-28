package com.dailybeat.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.data.model.Event
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onGenerateFromToday: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val events by viewModel.todayEvents.collectAsStateWithLifecycle()
    val dairyState by viewModel.dairyState.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.today_events_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.manual_event_label)) },
                minLines = 2,
            )
        }

        item {
            Button(
                onClick = {
                    viewModel.addManualEvent(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
            ) {
                Text(stringResource(R.string.save_event))
            }
        }

        if (events.isNotEmpty()) {
            item {
                Button(onClick = viewModel::generateTodayDairy, enabled = !dairyState.isGenerating) {
                    Text(stringResource(R.string.generate_today_dairy))
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            val text = viewModel.todayEventsText()
                            if (text.isNotBlank()) {
                                onGenerateFromToday(text)
                            }
                        }
                    },
                ) {
                    Text(stringResource(R.string.generate_from_today))
                }
            }
        }

        if (!viewModel.modelAvailable) {
            item {
                Text(
                    text = stringResource(R.string.model_missing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (dairyState.isGenerating) {
            item { CircularProgressIndicator() }
        }

        dairyState.error?.let { error ->
            item {
                Text(text = error, color = MaterialTheme.colorScheme.error)
            }
        }

        if (dairyState.text.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.daily_diary_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = dairyState.text,
                    onValueChange = viewModel::updateDairyText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.edit_dairy_label)) },
                    minLines = 6,
                )
            }
        }

        if (events.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_events_today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                Text(
                    text = stringResource(R.string.event_list_label),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(events, key = { it.id }) { event ->
                EventCard(event = event)
            }
        }
    }
}

@Composable
private fun EventCard(event: Event) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "$time · ${event.type}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = event.rawText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
