package com.dailybeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.ui.theme.EventCall
import com.dailybeat.app.ui.theme.EventGps
import com.dailybeat.app.ui.theme.EventManual
import com.dailybeat.app.ui.theme.EventMoment
import com.dailybeat.app.ui.theme.EventVisit
import com.dailybeat.app.ui.theme.EventVoice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EventCard(
    event: Event,
    modifier: Modifier = Modifier,
) {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))
    val accent = eventTypeColor(event.type)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(4.dp)
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .padding(vertical = 14.dp, horizontal = 4.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TypeChip(label = event.type, accent = accent)
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(text = event.rawText, style = MaterialTheme.typography.bodyLarge)
                event.placeName?.let { place ->
                    MetaLine("📍 $place")
                }
                event.peopleMentioned?.let { people ->
                    MetaLine("👤 $people")
                }
                event.caseNumbers?.let { cases ->
                    MetaLine("📋 $cases")
                }
            }
        }
    }
}

@Composable
private fun TypeChip(label: String, accent: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.14f),
    ) {
        Text(
            text = label.uppercase(Locale.getDefault()),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
    }
}

@Composable
private fun MetaLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun eventTypeColor(type: String): androidx.compose.ui.graphics.Color = when (type.lowercase()) {
    "voice" -> EventVoice
    "gps" -> EventGps
    "visit" -> EventVisit
    "moment" -> EventMoment
    "call" -> EventCall
    else -> EventManual
}
