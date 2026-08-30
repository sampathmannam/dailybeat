package com.dailybeat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SettingsGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DailyBeatScreenHeader(title = stringResource(R.string.settings_title))
        }

        item {
            SettingsGroup(title = stringResource(R.string.officer_name_label)) {
                OutlinedTextField(
                    value = state.officerName,
                    onValueChange = viewModel::setOfficerName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.officer_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = state.supervisorName,
                    onValueChange = viewModel::setSupervisorName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.supervisor_name_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_cloud_group)) {
                Text(
                    text = stringResource(R.string.settings_cloud_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    label = stringResource(R.string.cloud_llm_enabled),
                    checked = state.cloudLlmEnabled,
                    onCheckedChange = viewModel::setCloudLlmEnabled,
                )
                OutlinedTextField(
                    value = if (state.apiKeyDraft.isNotEmpty()) state.apiKeyDraft else {
                        if (state.hasApiKey) "••••••••••••••••" else ""
                    },
                    onValueChange = viewModel::setApiKeyDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_api_key_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                PrimaryButton(
                    text = stringResource(R.string.save_api_key),
                    onClick = viewModel::saveApiKey,
                    enabled = state.apiKeyDraft.isNotBlank(),
                )
                OutlinedTextField(
                    value = state.cloudModel,
                    onValueChange = viewModel::setCloudModel,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_model_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                if (state.cloudProvider == CloudProvider.COMPATIBLE.id) {
                    OutlinedTextField(
                        value = state.cloudBaseUrl,
                        onValueChange = viewModel::setCloudBaseUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.cloud_base_url_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CloudProvider.entries.forEach { provider ->
                        SecondaryProviderChip(
                            label = provider.displayName,
                            selected = state.cloudProvider == provider.id,
                            onClick = { viewModel.setCloudProvider(provider.id) },
                        )
                    }
                }
                SecondaryButton(
                    text = stringResource(R.string.test_cloud_connection),
                    onClick = viewModel::testCloudConnection,
                    enabled = !state.cloudTesting,
                )
                state.cloudTestResult?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.bodySmall)
                }
                ToggleRow(
                    label = stringResource(R.string.auto_evening_report),
                    checked = state.autoEveningReport,
                    onCheckedChange = viewModel::setAutoEveningReport,
                )
                ToggleRow(
                    label = stringResource(R.string.auto_midday_pulse),
                    checked = state.autoMiddayPulse,
                    onCheckedChange = viewModel::setAutoMiddayPulse,
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_qa_group)) {
                SecondaryButton(
                    text = stringResource(R.string.load_synthetic_day),
                    onClick = viewModel::seedSyntheticData,
                    enabled = !state.isSeedingSynthetic,
                )
                state.syntheticResult?.let { msg ->
                    Text(text = msg, style = MaterialTheme.typography.bodySmall)
                }
                SecondaryButton(
                    text = stringResource(R.string.refresh_audit_log),
                    onClick = viewModel::loadAuditLog,
                )
                state.auditLines.takeLast(8).forEach { line ->
                    Text(text = line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_capture_group)) {
                ToggleRow(
                    label = stringResource(R.string.gps_capture_label),
                    checked = state.gpsEnabled,
                    onCheckedChange = viewModel::setGpsEnabled,
                )
                ToggleRow(
                    label = stringResource(R.string.call_log_label),
                    checked = state.callLogEnabled,
                    onCheckedChange = viewModel::setCallLogEnabled,
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.places_title)) {
                if (state.placeSuggestions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.suggested_places_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.placeSuggestions.forEach { suggestion ->
                        SecondaryButton(
                            text = "${suggestion.name} (${suggestion.visitCount} visits)",
                            onClick = { viewModel.addSuggestedPlace(suggestion) },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.placeName,
                    onValueChange = { viewModel.updatePlaceDraft(it, state.placeLat, state.placeLon) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.place_name_label)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = state.placeLat,
                    onValueChange = { viewModel.updatePlaceDraft(state.placeName, it, state.placeLon) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.place_lat_label)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = state.placeLon,
                    onValueChange = { viewModel.updatePlaceDraft(state.placeName, state.placeLat, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.place_lon_label)) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                PrimaryButton(text = stringResource(R.string.add_place_button), onClick = viewModel::addPlace)
                state.placeError?.let { error ->
                    Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        items(state.places, key = { it.id }) { place ->
            PlaceCard(place = place, onDelete = { viewModel.deletePlace(place) })
        }
    }
}

@Composable
private fun SecondaryProviderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PlaceCard(place: Place, onDelete: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = place.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${place.latitude}, ${place.longitude} (${place.radiusM}m)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_place))
            }
        }
    }
}
