package com.dailybeat.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.dailybeat.app.ui.components.InlineFeedback
import com.dailybeat.app.ui.components.PrimaryButton
import com.dailybeat.app.ui.components.SecondaryButton
import com.dailybeat.app.ui.components.SettingsGroup
import com.dailybeat.app.ui.components.readableContentWidth
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator

private enum class SettingsSection {
    CAPTURE_AND_PLACES,
    PRIVACY_AND_DATA,
    JOURNAL_AND_APPEARANCE,
    BACKUP_AND_CLOUD,
    DEVELOPER,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var supportPreview by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    supportPreview?.let { preview ->
        val supportContext = androidx.compose.ui.platform.LocalContext.current
        AlertDialog(onDismissRequest = { supportPreview = null }, title = { Text("Review support details") },
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text("No locations, notes, names, accounts or credentials are included."); Text(preview) } },
            confirmButton = { TextButton(onClick = {
                supportContext.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, preview), "Share support details"))
                supportPreview = null
            }) { Text("Choose sharing app") } },
            dismissButton = { TextButton(onClick = { supportPreview = null }) { Text("Cancel") } })
    }
    val showQaTools = booleanResource(R.bool.show_qa_tools)
    val settingsContext = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var placePendingDeletion by remember { mutableStateOf<Place?>(null) }
    var localErasePhrase by remember { mutableStateOf("") }
    var activeSectionKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeSection = activeSectionKey?.let(SettingsSection::valueOf)

    BackHandler(enabled = activeSection != null) {
        activeSectionKey = null
    }

    LaunchedEffect(state.localDataErased) {
        if (state.localDataErased) (settingsContext as? android.app.Activity)?.recreate()
    }

    state.pendingRetentionDays?.let { days ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRetentionChange,
            title = { Text(stringResource(R.string.retention_confirm_title, days)) },
            text = { Text(stringResource(R.string.retention_confirm_warning, days)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmRetentionChange,
                    enabled = !state.dataBusy,
                    modifier = Modifier.testTag("confirm_retention_change"),
                ) { Text(stringResource(R.string.delete_older_history)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelRetentionChange) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.cloudDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelCloudDataDeletion,
            title = { Text(stringResource(R.string.delete_cloud_data_title)) },
            text = { Text(stringResource(R.string.delete_cloud_data_warning)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmCloudDataDeletion,
                    enabled = !state.dataBusy,
                    modifier = Modifier.testTag("confirm_delete_cloud_data"),
                ) { Text(stringResource(R.string.delete_cloud_data)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelCloudDataDeletion) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.accountDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::cancelAccountDeletion,
            title = { Text(stringResource(R.string.delete_cloud_account_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.delete_cloud_account_warning))
                    OutlinedTextField(
                        value = state.accountDeletePassword,
                        onValueChange = viewModel::setAccountDeletePassword,
                        modifier = Modifier.fillMaxWidth().testTag("delete_account_password"),
                        label = { Text(stringResource(R.string.current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmAccountDeletion,
                    enabled = !state.dataBusy && state.accountDeletePassword.isNotBlank(),
                    modifier = Modifier.testTag("confirm_delete_cloud_account"),
                ) { Text(stringResource(R.string.delete_account)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelAccountDeletion) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (state.localEraseConfirmation) {
        AlertDialog(
            onDismissRequest = {
                localErasePhrase = ""
                viewModel.cancelLocalDataErase()
            },
            title = { Text(stringResource(R.string.erase_phone_data_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.erase_phone_data_warning))
                    OutlinedTextField(
                        value = localErasePhrase,
                        onValueChange = { localErasePhrase = it.take(6) },
                        modifier = Modifier.fillMaxWidth().testTag("erase_phone_confirmation"),
                        label = { Text(stringResource(R.string.type_delete)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        localErasePhrase = ""
                        viewModel.confirmLocalDataErase()
                    },
                    enabled = !state.dataBusy && localErasePhrase == "DELETE",
                    modifier = Modifier.testTag("confirm_erase_phone_data"),
                ) { Text(stringResource(R.string.erase_phone_data)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    localErasePhrase = ""
                    viewModel.cancelLocalDataErase()
                }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

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
            onDismissRequest = { if (!state.placeBusy) placePendingDeletion = null },
            title = { Text(stringResource(R.string.delete_place_title)) },
            text = { Text(stringResource(R.string.delete_place_warning, place.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlace(place)
                        placePendingDeletion = null
                    },
                    enabled = !state.placeBusy,
                    modifier = Modifier.testTag("confirm_delete_place"),
                ) { Text(stringResource(R.string.delete_place_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { placePendingDeletion = null },
                    enabled = !state.placeBusy,
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
            .readableContentWidth()
            .imePadding()
            .testTag("settings_list")
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (activeSection == null) {
            item {
                DailyBeatScreenHeader(
                    title = stringResource(R.string.settings_title),
                    subtitle = stringResource(R.string.settings_subtitle),
                )
            }
        } else {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsDetailHeader(
                        title = settingsSectionTitle(activeSection),
                        onBack = { activeSectionKey = null },
                    )
                }
            }
        }

        state.screenError?.let { error ->
            item {
                InlineFeedback(
                    message = error,
                    isError = true,
                    actionLabel = stringResource(R.string.feed_load_retry),
                    onAction = viewModel::refresh,
                )
            }
        }

        if (activeSection == null) {
            item {
                SettingsCategoryMenu(
                    showQaTools = showQaTools,
                    onOpen = { activeSectionKey = it.name },
                )
            }
        }

        if (activeSection == SettingsSection.CAPTURE_AND_PLACES) item {
            SettingsGroup(title = stringResource(R.string.settings_capture_group)) {
                ToggleRow(
                    label = stringResource(R.string.gps_capture_label),
                    checked = state.gpsEnabled,
                    onCheckedChange = viewModel::setGpsEnabled,
                )
                Text(if (com.dailybeat.app.BuildConfig.GOOGLE_LOCATION)
                    "Motion-aware capture uses Google location services. Battery results depend on your device and route."
                else "Google-free build · Uses Android location providers with batched updates. Motion-triggered idle sleep is unavailable; battery use must be measured separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // Amber, not red: this is advice about a phone setting, not a failure.
                    color = if (state.batteryUnrestricted) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
                if (!state.batteryUnrestricted) {
                    SecondaryButton(
                        text = stringResource(R.string.battery_open_settings),
                        onClick = { openBatterySettings(settingsContext) },
                    )
                }
                state.captureMessage?.let { message ->
                    InlineFeedback(message = message, isError = true)
                    SecondaryButton(
                        text = stringResource(R.string.open_app_settings),
                        onClick = { openAppSettings(settingsContext) },
                    )
                }
            }
        }

        if (activeSection == SettingsSection.CAPTURE_AND_PLACES) item {
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
                            enabled = !state.placeBusy,
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
                    enabled = !state.placeBusy && !state.placeLocating,
                    singleLine = true,
                )
                // The officer no longer types coordinates: one tap reads the current GPS fix.
                val locationCaptured = state.placeLat.isNotBlank() && state.placeLon.isNotBlank()
                SecondaryButton(
                    text = if (locationCaptured) {
                        stringResource(R.string.use_current_location_again)
                    } else {
                        stringResource(R.string.use_current_location)
                    },
                    onClick = viewModel::captureCurrentLocationForPlace,
                    enabled = !state.placeLocating && !state.placeBusy,
                )
                if (state.placeLocating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.place_locating),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (locationCaptured) {
                    Text(
                        stringResource(R.string.place_location_captured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("place_captured_readout"),
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.add_place_button),
                    onClick = viewModel::addPlace,
                    enabled = state.placeName.isNotBlank() && locationCaptured &&
                        !state.placeLocating && !state.placeBusy,
                )
                state.placeError?.let { error ->
                    InlineFeedback(message = error, isError = true)
                }
            }
        }

        if (activeSection == SettingsSection.CAPTURE_AND_PLACES) {
            items(state.places, key = { it.id }) { place ->
                PlaceCard(
                    place = place,
                    onPrivateChange = { viewModel.setPlacePrivate(place, it) },
                    onDelete = { placePendingDeletion = place },
                    enabled = !state.placeBusy,
                )
            }
        }

        if (activeSection == SettingsSection.PRIVACY_AND_DATA) item {
            SettingsGroup(title = stringResource(R.string.settings_data_privacy_group)) {
                Text(
                    stringResource(R.string.retention_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    0 to stringResource(R.string.retention_forever),
                    30 to stringResource(R.string.retention_30_days),
                    90 to stringResource(R.string.retention_90_days),
                    365 to stringResource(R.string.retention_one_year),
                ).forEach { (days, label) ->
                    RetentionOption(
                        label = label,
                        selected = state.historyRetentionDays == days,
                        enabled = !state.dataBusy,
                        onClick = { viewModel.requestRetentionChange(days) },
                        modifier = Modifier.testTag("retention_$days"),
                    )
                }
                Text(
                    stringResource(R.string.retention_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.dataMessage?.let { message ->
                    InlineFeedback(message = message, isError = state.dataMessageIsError)
                }
                if (state.dataBusy) BusyRow(stringResource(R.string.data_working))
                TextButton(
                    onClick = viewModel::requestLocalDataErase,
                    enabled = !state.dataBusy && !state.backupBusy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("erase_phone_data"),
                ) {
                    Text(
                        stringResource(R.string.erase_phone_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SecondaryButton(
                    text = stringResource(R.string.legal_notices_title),
                    onClick = {
                        settingsContext.startActivity(
                            android.content.Intent(settingsContext, com.dailybeat.app.LegalNoticesActivity::class.java),
                        )
                    },
                    enabled = !state.dataBusy,
                )
            }
        }

        if (activeSection == SettingsSection.JOURNAL_AND_APPEARANCE) item {
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

        if (activeSection == SettingsSection.JOURNAL_AND_APPEARANCE) item {
            SettingsGroup(title = stringResource(R.string.settings_identity_group)) {
                com.dailybeat.app.ui.components.JournalProfilePicker(
                    selected = state.journalProfile,
                    onSelected = viewModel::setJournalProfile,
                )
                OutlinedTextField(
                    value = state.officerName,
                    onValueChange = viewModel::setOfficerName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (state.journalProfile == com.dailybeat.app.data.settings.JournalProfile.POLICE) "Officer name" else "Your name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )
                if (state.journalProfile == com.dailybeat.app.data.settings.JournalProfile.POLICE) OutlinedTextField(
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

        if (activeSection == SettingsSection.BACKUP_AND_CLOUD) item {
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
                        Text("New backups are encrypted on this phone. Use a separate recovery passphrase (20–256 characters, ideally six random words). Keep it in a password manager; losing it means losing access to the backup. It is not your account password.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = state.recoveryPassphrase,
                            onValueChange = viewModel::setRecoveryPassphrase,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Recovery passphrase") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !state.backupBusy,
                        )
                        OutlinedTextField(
                            value = state.recoveryConfirmation,
                            onValueChange = viewModel::setRecoveryConfirmation,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm passphrase for a new backup") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            enabled = !state.backupBusy,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(
                                checked = state.legacyBackupRestore,
                                onCheckedChange = viewModel::setLegacyBackupRestore,
                                enabled = !state.backupBusy,
                            )
                            Text("Restore an older, unencrypted backup instead", style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.legacyBackupRestore) Text("Legacy recovery does not use this passphrase. After checking the restored data, create an encrypted backup. Older cloud copies remain until you delete them separately.",
                            style = MaterialTheme.typography.bodySmall)
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
                        SecondaryButton(
                            text = stringResource(R.string.delete_cloud_data),
                            onClick = viewModel::requestCloudDataDeletion,
                            enabled = !state.backupBusy && !state.dataBusy,
                            modifier = Modifier.testTag("delete_cloud_data"),
                        )
                        TextButton(
                            onClick = viewModel::requestAccountDeletion,
                            enabled = !state.backupBusy && !state.dataBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("delete_cloud_account"),
                        ) {
                            Text(
                                stringResource(R.string.delete_cloud_account),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                state.backupMessage?.let { message ->
                    InlineFeedback(message = message, isError = state.backupMessageIsError)
                }
                if (state.backupSignedInEmail != null) {
                    TextButton(onClick = viewModel::loadBackupHistory, enabled = !state.backupBusy && !state.dataBusy) { Text("Backup history · latest five versions") }
                    state.backupVersions.forEach { version ->
                        TextButton(onClick = { viewModel.selectBackupVersion(version.id) }, enabled = !state.backupBusy && !state.dataBusy) {
                            Text((if (state.selectedBackupVersion == version.id) "Selected · " else "") + version.createdAt)
                        }
                    }
                    if (state.selectedBackupVersion != null) TextButton(onClick = { viewModel.selectBackupVersion(null) }) { Text("Use latest backup") }
                }
                if (state.backupBusy) {
                    BusyRow(stringResource(R.string.backup_working))
                }
                Text(
                    text = stringResource(R.string.backup_api_key_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (activeSection == SettingsSection.PRIVACY_AND_DATA) item {
            val supportContext = androidx.compose.ui.platform.LocalContext.current
            SettingsGroup(title = "Support") {
                Text("Review a small report of app settings and permissions to help diagnose capture problems.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { supportPreview = com.dailybeat.app.audit.SupportDiagnostics.build(supportContext.applicationContext as com.dailybeat.app.DailyBeatApp) }) { Text("Prepare support details") }
            }
        }
        if (activeSection == SettingsSection.CAPTURE_AND_PLACES) item {
            SettingsGroup(title = "Place name lookup") {
                Text("Saved places work offline. Optional automatic address lookups send coordinates outside private zones to your managed provider. Leave empty to turn off automatic lookups. Online maps still contact map tile providers.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = state.geocodingEndpoint, onValueChange = viewModel::setGeocodingDraft,
                    label = { Text("Managed HTTPS endpoint") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = viewModel::saveGeocodingEndpoint) { Text("Save lookup setting") }
                state.geocodingMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (activeSection == SettingsSection.BACKUP_AND_CLOUD) item {
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
                                enabled = !state.apiKeyBusy && !state.cloudTesting,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
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
                SecondaryButton(
                    text = stringResource(R.string.test_cloud_connection),
                    onClick = viewModel::testCloudConnection,
                    enabled = !state.cloudTesting && !state.apiKeyBusy,
                )
                state.cloudTestResult?.let { msg ->
                    InlineFeedback(message = msg, isError = state.cloudTestIsError)
                }
                if (state.cloudTesting || state.apiKeyBusy) {
                    BusyRow(stringResource(R.string.cloud_working))
                }
                ToggleRow(
                    label = stringResource(R.string.auto_evening_report),
                    checked = state.autoEveningReport,
                    onCheckedChange = viewModel::setAutoEveningReport,
                )
            }
        }

        if (showQaTools && activeSection == SettingsSection.DEVELOPER) {
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
    }
}

@Composable
private fun settingsSectionTitle(section: SettingsSection): String = when (section) {
    SettingsSection.CAPTURE_AND_PLACES -> stringResource(R.string.settings_category_capture_title)
    SettingsSection.PRIVACY_AND_DATA -> stringResource(R.string.settings_category_privacy_title)
    SettingsSection.JOURNAL_AND_APPEARANCE -> stringResource(R.string.settings_category_journal_title)
    SettingsSection.BACKUP_AND_CLOUD -> stringResource(R.string.settings_category_connected_title)
    SettingsSection.DEVELOPER -> stringResource(R.string.settings_category_developer_title)
}

@Composable
private fun SettingsDetailHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("settings_back"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SettingsCategoryMenu(
    showQaTools: Boolean,
    onOpen: (SettingsSection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsCategoryButton(
            title = stringResource(R.string.settings_category_capture_title),
            subtitle = stringResource(R.string.settings_category_capture_summary),
            testTag = "settings_category_capture",
            onClick = { onOpen(SettingsSection.CAPTURE_AND_PLACES) },
        )
        SettingsCategoryButton(
            title = stringResource(R.string.settings_category_privacy_title),
            subtitle = stringResource(R.string.settings_category_privacy_summary),
            testTag = "settings_category_privacy",
            onClick = { onOpen(SettingsSection.PRIVACY_AND_DATA) },
        )
        SettingsCategoryButton(
            title = stringResource(R.string.settings_category_journal_title),
            subtitle = stringResource(R.string.settings_category_journal_summary),
            testTag = "settings_category_journal",
            onClick = { onOpen(SettingsSection.JOURNAL_AND_APPEARANCE) },
        )
        SettingsCategoryButton(
            title = stringResource(R.string.settings_category_connected_title),
            subtitle = stringResource(R.string.settings_category_connected_summary),
            testTag = "settings_category_connected",
            onClick = { onOpen(SettingsSection.BACKUP_AND_CLOUD) },
        )
        if (showQaTools) {
            SettingsCategoryButton(
                title = stringResource(R.string.settings_category_developer_title),
                subtitle = stringResource(R.string.settings_category_developer_summary),
                testTag = "settings_category_developer",
                onClick = { onOpen(SettingsSection.DEVELOPER) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryButton(
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .testTag(testTag)
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondaryProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    // Selection used to be a 1.19:1 background tint with no check, no role and a 28 dp target.
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
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
private fun RetentionOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlaceCard(
    place: Place,
    onPrivateChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean,
) {
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
                    text = stringResource(R.string.saved_place_radius, place.radiusM),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = place.isPrivate,
                            enabled = enabled,
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
                enabled = enabled,
                modifier = Modifier.testTag("delete_place_${place.id}"),
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_place))
            }
        }
    }
}

@Composable
private fun BusyRow(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
