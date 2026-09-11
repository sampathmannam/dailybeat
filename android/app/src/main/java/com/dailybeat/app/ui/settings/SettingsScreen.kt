package com.dailybeat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.R
import com.dailybeat.app.data.model.Place
import com.dailybeat.app.data.settings.CloudProvider
import com.dailybeat.app.data.settings.ThemePreference
import com.dailybeat.app.ui.components.DailyBeatScreenHeader
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SettingsGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.Role
import java.util.Locale
import androidx.compose.foundation.layout.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val showQaTools = booleanResource(R.bool.show_qa_tools)
    val settingsContext = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var placePendingDeletion by remember { mutableStateOf<Place?>(null) }

    if (state.apiKeyRemovalConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelApiKeyRemoval,
            title = { Text(stringResource(R.string.remove_api_key_title)) },
            text = { Text(stringResource(R.string.remove_api_key_warning)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmApiKeyRemoval,
                    enabled = !state.apiKeyBusy,
                    modifier = Modifier.testTag("confirm_remove_api_key"),
                ) { Text(stringResource(R.string.remove_api_key_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelApiKeyRemoval) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    placePendingDeletion?.let { place ->
        AlertDialog(
            onDismissRequest = { placePendingDeletion = null },
            title = { Text(stringResource(R.string.delete_place_title)) },
            text = { Text(stringResource(R.string.delete_place_warning, place.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlace(place)
                        placePendingDeletion = null
                    },
                    modifier = Modifier.testTag("confirm_delete_place"),
                ) { Text(stringResource(R.string.delete_place_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { placePendingDeletion = null },
                    modifier = Modifier.testTag("cancel_delete_place"),
                ) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedContainerColor = MaterialTheme.colorScheme.background,
        unfocusedContainerColor = MaterialTheme.colorScheme.background,
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("settings_list")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DailyBeatScreenHeader(title = stringResource(R.string.settings_title))
        }

        item {
            SettingsGroup(title = stringResource(R.string.settings_appearance_group)) {
                Text(
                    text = stringResource(R.string.settings_theme_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("theme_selector"),
                ) {
                    ThemePreference.entries.forEachIndexed { index, preference ->
                        SegmentedButton(
                            selected = state.themePreference == preference,
                            onClick = { viewModel.setThemePreference(preference) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemePreference.entries.size,
                            ),
                            modifier = Modifier.testTag("theme_${preference.id}"),
                        ) {
                            Text(
                                text = when (preference) {
                                    ThemePreference.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemePreference.LIGHT -> stringResource(R.string.theme_light)
                                    ThemePreference.DARK -> stringResource(R.string.theme_dark)
                                },
                            )
                        }
                    }
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
                if (state.capturePausedUntilMs > System.currentTimeMillis()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.capture_paused_body),
                            Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    PrimaryButton(
                        text = stringResource(R.string.resume_capture_now),
                        onClick = viewModel::resumeCaptureNow,
                    )
                } else {
                    SecondaryButton(
                        text = stringResource(R.string.pause_capture_one_hour),
                        onClick = viewModel::pauseCaptureForOneHour,
                        enabled = state.gpsEnabled,
                    )
                }
                Text(
                    text = if (state.batteryUnrestricted) {
                        stringResource(R.string.battery_unrestricted)
                    } else {
                        stringResource(R.string.battery_restricted)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.batteryUnrestricted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (!state.batteryUnrestricted) {
                    SecondaryButton(
                        text = stringResource(R.string.battery_open_settings),
                        onClick = { openBatterySettings(settingsContext) },
                    )
                }
                state.captureMessage?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    SecondaryButton(
                        text = stringResource(R.string.open_app_settings),
                        onClick = { openAppSettings(settingsContext) },
                    )
                }
            }
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
            SettingsGroup(title = stringResource(R.string.settings_backup_group)) {
                Text(
                    text = stringResource(R.string.settings_backup_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    !state.backupConfigured -> {
                        Text(
                            text = stringResource(R.string.backup_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.backupSignedInEmail == null -> {
                        OutlinedTextField(
                            value = state.backupEmailDraft,
                            onValueChange = viewModel::setBackupEmail,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.backup_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = state.backupPasswordDraft,
                            onValueChange = viewModel::setBackupPassword,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.backup_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.backup_sign_in),
                            onClick = viewModel::signInToBackup,
                            enabled = !state.backupBusy &&
                                state.backupEmailDraft.isNotBlank() &&
                                state.backupPasswordDraft.isNotBlank(),
                        )
                        SecondaryButton(
                            text = stringResource(R.string.backup_create_account),
                            onClick = viewModel::createBackupAccount,
                            enabled = !state.backupBusy &&
                                state.backupEmailDraft.isNotBlank() &&
                                state.backupPasswordDraft.length >= 8,
                        )
                    }
                    else -> {
                        Text(
                            text = stringResource(R.string.backup_signed_in_as, state.backupSignedInEmail.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.backup_now),
                            onClick = viewModel::backupNow,
                            enabled = !state.backupBusy,
                        )
                        SecondaryButton(
                            text = stringResource(R.string.backup_restore),
                            onClick = viewModel::requestBackupRestore,
                            enabled = !state.backupBusy,
                        )
                        if (state.backupRestoreConfirmation) {
                            Text(
                                text = stringResource(R.string.backup_restore_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            PrimaryButton(
                                text = stringResource(R.string.backup_restore_confirm),
                                onClick = viewModel::confirmBackupRestore,
                                enabled = !state.backupBusy,
                            )
                            SecondaryButton(
                                text = stringResource(R.string.cancel),
                                onClick = viewModel::cancelBackupRestore,
                            )
                        }
                        SecondaryButton(
                            text = stringResource(R.string.backup_sign_out),
                            onClick = viewModel::signOutOfBackup,
                            enabled = !state.backupBusy,
                        )
                    }
                }
                state.backupMessage?.let { message ->
                    Text(text = message, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = stringResource(R.string.backup_api_key_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    value = state.apiKeyDraft,
                    onValueChange = viewModel::setApiKeyDraft,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cloud_api_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                if (state.hasApiKey) {
                    Text(
                        text = stringResource(R.string.cloud_api_key_saved_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryButton(
                        text = stringResource(R.string.remove_api_key),
                        onClick = viewModel::requestApiKeyRemoval,
                        enabled = !state.apiKeyBusy && !state.cloudTesting,
                        modifier = Modifier.testTag("remove_api_key"),
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.save_api_key),
                    onClick = viewModel::saveApiKey,
                    enabled = state.apiKeyDraft.isNotBlank() &&
                        !state.apiKeyBusy && !state.cloudTesting,
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
                CloudProvider.entries.toList().chunked(2).forEach { providers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        providers.forEach { provider ->
                            SecondaryProviderChip(
                                label = provider.displayName,
                                selected = state.cloudProvider == provider.id,
                                onClick = { viewModel.setCloudProvider(provider.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                SecondaryButton(
                    text = stringResource(R.string.test_cloud_connection),
                    onClick = viewModel::testCloudConnection,
                    enabled = !state.cloudTesting && !state.apiKeyBusy,
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

        if (showQaTools) {
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
            PlaceCard(
                place = place,
                onPrivateChange = { viewModel.setPlacePrivate(place, it) },
                onDelete = { placePendingDeletion = place },
            )
        }
    }
}

@Composable
private fun SecondaryProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Selection used to be a 1.19:1 background tint with no check, no role and a 28 dp target.
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row toggles and carries the label, so TalkBack announces the setting instead of
    // "switch, on", and the target is the row rather than the 52 dp switch alone.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun PlaceCard(place: Place, onPrivateChange: (Boolean) -> Unit, onDelete: () -> Unit) {
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
            Column(Modifier.weight(1f)) {
                Text(text = place.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f · %d m", place.latitude, place.longitude, place.radiusM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = place.isPrivate,
                            role = Role.Switch,
                            onValueChange = onPrivateChange,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.private_place),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.private_place_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = place.isPrivate,
                        onCheckedChange = null,
                        modifier = Modifier.testTag("private_place_${place.id}"),
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_place_${place.id}"),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_place))
            }
        }
    }
}

/**
 * Sends the officer to the system screen where DailyBeat can be set to Unrestricted. The app
 * deliberately does not ask for the exemption directly; the system list is the honest route and
 * needs no extra permission.
 */
private fun openBatterySettings(context: android.content.Context) {
    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.fromParts("package", context.packageName, null))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
