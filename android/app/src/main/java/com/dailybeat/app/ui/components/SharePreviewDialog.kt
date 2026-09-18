package com.dailybeat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dailybeat.app.export.DiarySharePreview

@Composable
fun SharePreviewDialog(
    previews: List<DiarySharePreview>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Review sharing copy") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()).testTag("share_preview"),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (previews.size == 1) "PDF • Shared only after you choose a destination"
                    else "ZIP • ${previews.size} days as PDF and text • No diagnostic logs",
                    style = MaterialTheme.typography.labelLarge)
                previews.forEach { preview ->
                    Text(preview.date.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(preview.explanation, style = MaterialTheme.typography.bodySmall)
                    if (preview.author.isNotBlank()) Text("Name: ${preview.author}")
                    if (preview.supervisor.isNotBlank()) Text("Supervisor: ${preview.supervisor}")
                    Text(preview.profile.documentTitle, style = MaterialTheme.typography.titleSmall)
                    Text(preview.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy, modifier = Modifier.testTag("confirm_share")) {
                Text(if (busy) "Preparing…" else "Choose sharing app")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
