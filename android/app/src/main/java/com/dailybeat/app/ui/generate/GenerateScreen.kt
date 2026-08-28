package com.dailybeat.app.ui.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R

@Composable
fun GenerateScreen(
    prefilledEvents: String? = null,
    modifier: Modifier = Modifier,
    viewModel: GenerateViewModel = viewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    LaunchedEffect(prefilledEvents) {
        if (!prefilledEvents.isNullOrBlank()) {
            viewModel.setEventsText(prefilledEvents)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.generate_title),
            style = MaterialTheme.typography.headlineSmall,
        )

        if (!state.modelAvailable) {
            Text(
                text = stringResource(R.string.model_missing_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = state.eventsText,
            onValueChange = viewModel::setEventsText,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.events_input_label)) },
            minLines = 4,
        )

        Button(
            onClick = viewModel::generate,
            enabled = !state.isLoading && state.eventsText.isNotBlank(),
        ) {
            Text(stringResource(R.string.generate_button))
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (state.output.isNotBlank()) {
            Text(
                text = stringResource(R.string.dairy_output_label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(text = state.output, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
