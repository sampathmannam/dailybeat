package com.dailybeat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
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
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import com.dailybeat.app.ui.theme.LocalDarkTheme
import com.dailybeat.app.ui.theme.EventCallNight
import com.dailybeat.app.ui.theme.EventGpsNight
import com.dailybeat.app.ui.theme.EventManualNight
import com.dailybeat.app.ui.theme.EventMomentNight
import com.dailybeat.app.ui.theme.EventVisitNight
import com.dailybeat.app.ui.theme.EventVoiceNight
import com.dailybeat.app.util.Formatters

@Composable
fun EventCard(
    event: Event,
    modifier: Modifier = Modifier,
) {
    val time = Formatters.clock(event.timestamp)
    val accent = eventTypeColor(event.type)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Column(
                modifier = Modifier
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
                    MetaLine(Icons.Outlined.Place, place)
                }
                event.peopleMentioned?.let { people ->
                    MetaLine(Icons.Outlined.Person, people)
                }
                event.caseNumbers?.let { cases ->
                    MetaLine(Icons.Outlined.Assignment, cases)
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
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String) {
    // A real icon, not an emoji glued to the front of the value: TalkBack used to read "round
    // pushpin" before every place name.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The light accents measured 2.1–2.8:1 on the night surface; dark theme gets a lifted set. */
@Composable
private fun eventTypeColor(type: String): androidx.compose.ui.graphics.Color {
    val dark = LocalDarkTheme.current
    return when (type.lowercase()) {
        "voice" -> if (dark) EventVoiceNight else EventVoice
        "gps" -> if (dark) EventGpsNight else EventGps
        "visit" -> if (dark) EventVisitNight else EventVisit
        "moment" -> if (dark) EventMomentNight else EventMoment
        "call" -> if (dark) EventCallNight else EventCall
        else -> if (dark) EventManualNight else EventManual
    }
}
