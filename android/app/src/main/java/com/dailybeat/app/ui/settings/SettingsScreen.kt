package com.dailybeat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.data.model.Place

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item {
            OutlinedTextField(
                value = state.officerName,
                onValueChange = viewModel::setOfficerName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.officer_name_label)) },
            )
        }

        item {
            ToggleRow(
                label = stringResource(R.string.gps_capture_label),
                checked = state.gpsEnabled,
                onCheckedChange = viewModel::setGpsEnabled,
            )
        }

        item {
            ToggleRow(
                label = stringResource(R.string.call_log_label),
                checked = state.callLogEnabled,
                onCheckedChange = viewModel::setCallLogEnabled,
            )
        }

        item {
            Text(
                text = if (state.modelImported) {
                    stringResource(R.string.model_ready)
                } else {
                    stringResource(R.string.model_import_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = viewModel::importModel) {
                Text(stringResource(R.string.import_model_button))
            }
        }

        item {
            Text(text = stringResource(R.string.places_title), style = MaterialTheme.typography.titleMedium)
        }

        item {
            OutlinedTextField(
                value = state.placeName,
                onValueChange = { viewModel.updatePlaceDraft(it, state.placeLat, state.placeLon) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.place_name_label)) },
            )
        }

        item {
            OutlinedTextField(
                value = state.placeLat,
                onValueChange = { viewModel.updatePlaceDraft(state.placeName, it, state.placeLon) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.place_lat_label)) },
            )
        }

        item {
            OutlinedTextField(
                value = state.placeLon,
                onValueChange = { viewModel.updatePlaceDraft(state.placeName, state.placeLat, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.place_lon_label)) },
            )
        }

        item {
            Button(onClick = viewModel::addPlace) {
                Text(stringResource(R.string.add_place_button))
            }
        }

        items(state.places, key = { it.id }) { place ->
            PlaceCard(place = place, onDelete = { viewModel.deletePlace(place) })
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun PlaceCard(place: Place, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = place.name, style = MaterialTheme.typography.titleMedium)
                Text(text = "${place.latitude}, ${place.longitude} (${place.radiusM}m)")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_place))
            }
        }
    }
}
