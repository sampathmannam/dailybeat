package com.dailybeat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.theme.EventGps
import com.dailybeat.app.ui.theme.Gold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun VisitCard(visit: LocationVisit, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val start = Instant.ofEpochMilli(visit.startMs).atZone(zone).format(timeFmt)
    val end = Instant.ofEpochMilli(visit.endMs).atZone(zone).format(timeFmt)
    val minutes = ((visit.endMs - visit.startMs) / 60000.0).roundToInt()
    val isTransit = visit.visitType == "transit"
    val accent = if (isTransit) Gold else EventGps

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isTransit) "In transit" else "Stay",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Text(
                    text = "$start – $end · ${minutes}m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = visit.placeName ?: visit.address ?: "Unknown location",
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!isTransit && visit.address != null && visit.placeName != null) {
                Text(
                    text = visit.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
