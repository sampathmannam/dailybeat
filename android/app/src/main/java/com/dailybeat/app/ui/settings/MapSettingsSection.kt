package com.dailybeat.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.R
import com.dailybeat.app.maps.MapDownloadStatus
import com.dailybeat.app.maps.MapProviderConfig
import com.dailybeat.app.ui.components.SettingsGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MapSettingsSection() {
    val context = LocalContext.current
    val app = context.applicationContext as DailyBeatApp
    val preferences by app.mapSettings.state.collectAsStateWithLifecycle()
    val offline by app.offlineMaps.state.collectAsStateWithLifecycle()
    val catalog = app.offlineMaps.catalog
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var mobile by rememberSaveable { mutableStateOf(app.offlineMaps.mobileDataAllowed()) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable(preferences.provider) { mutableStateOf(preferences.provider.styleUrl) }
    var raster by rememberSaveable(preferences.provider) { mutableStateOf(preferences.provider.rasterTileTemplate) }
    var attribution by rememberSaveable(preferences.provider) { mutableStateOf(preferences.provider.attribution) }
    fun action(block: suspend () -> Unit) {
        scope.launch {
            actionBusy = true
            message = null
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) { message = error.message }
            catch (_: Exception) { message = context.getString(R.string.map_failed_action) }
            finally { actionBusy = false }
        }
    }
    SettingsGroup(title = stringResource(R.string.map_settings_title)) {
        MapSwitch(stringResource(R.string.map_online_label), preferences.allowOnlineMaps, "allow_online_maps", enabled = !actionBusy) { allowed ->
            action { app.mapNetwork.cancelRequests(); app.mapSettings.setOnline(allowed) }
        }
        Text(stringResource(R.string.map_online_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.map_offline_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.map_download_size, Formatter.formatFileSize(context, catalog.downloadBytes), catalog.dataDate),
            style = MaterialTheme.typography.bodyMedium)
        offline.installed?.let {
            Text(stringResource(R.string.map_installed_date, it.dataDate), style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.map_download_hint), style = MaterialTheme.typography.bodySmall)
        val downloading = offline.status in setOf(MapDownloadStatus.WAITING, MapDownloadStatus.DOWNLOADING, MapDownloadStatus.VERIFYING)
        val busy = actionBusy || offline.status == MapDownloadStatus.DELETING
        if (!downloading) {
            MapSwitch(stringResource(R.string.map_mobile_data), mobile, "map_mobile_data", enabled = !busy) { mobile = it }
        }
        if (downloading || offline.downloadedBytes > 0 && offline.installed == null) {
            LinearProgressIndicator(progress = { (offline.downloadedBytes.toFloat() / catalog.downloadBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().testTag("offline_map_progress"))
            Text(stringResource(R.string.map_download_progress, Formatter.formatFileSize(context, offline.downloadedBytes),
                Formatter.formatFileSize(context, catalog.downloadBytes)), style = MaterialTheme.typography.bodySmall)
        }
        if (downloading) {
            TextButton(onClick = { action { app.offlineMaps.pause() } }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("pause_offline_map")) { Text(stringResource(R.string.map_pause)) }
        } else if (offline.installed?.version != catalog.version) {
            TextButton(onClick = { action { app.offlineMaps.start(mobile) } }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("download_offline_map")) {
                Text(stringResource(when {
                    offline.installed != null -> R.string.map_update
                    offline.status in setOf(MapDownloadStatus.PAUSED, MapDownloadStatus.FAILED) -> R.string.map_resume
                    else -> R.string.map_download
                }))
            }
        }
        if (offline.installed != null || offline.status != MapDownloadStatus.IDLE) {
            TextButton(onClick = { action { app.offlineMaps.delete() } }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("delete_offline_map")) { Text(stringResource(R.string.map_delete)) }
        }
        offline.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { advanced = !advanced }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.map_advanced))
        }
        if (advanced) {
            OutlinedTextField(value = style, onValueChange = { style = it.take(2048) },
                label = { Text(stringResource(R.string.map_style_url)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = raster, onValueChange = { raster = it.take(2048) },
                label = { Text(stringResource(R.string.map_raster_url)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = attribution, onValueChange = { attribution = it.take(160) },
                label = { Text(stringResource(R.string.map_attribution)) }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { action {
                app.mapNetwork.cancelRequests()
                app.mapSettings.setProvider(MapProviderConfig(style.trim(), raster.trim(), attribution.trim()))
                message = null
            } }, enabled = !busy) { Text(stringResource(R.string.map_save_provider)) }
            TextButton(onClick = { action { app.mapSettings.setProvider(MapProviderConfig()) } }, enabled = !busy) {
                Text(stringResource(R.string.map_reset_provider))
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MapSwitch(label: String, checked: Boolean, tag: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
