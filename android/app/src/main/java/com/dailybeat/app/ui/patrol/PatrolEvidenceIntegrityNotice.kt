package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun PatrolEvidenceIntegrityNotice(
    unreadableTrackPoints: Int,
    captureError: String?,
    modifier: Modifier = Modifier,
) {
    if (unreadableTrackPoints <= 0 && captureError.isNullOrBlank()) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("patrol_evidence_integrity_warning"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Route evidence needs attention", style = MaterialTheme.typography.titleMedium)
                captureError?.takeIf { it.isNotBlank() }?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                if (unreadableTrackPoints > 0) {
                    Text(
                        "$unreadableTrackPoints encrypted route ${if (unreadableTrackPoints == 1) "point is" else "points are"} stored but cannot be opened securely on this device. Report the device issue; do not treat the displayed route as complete.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
