package com.dailybeat.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.settings.JournalProfile

@Composable
fun JournalProfilePicker(selected: JournalProfile, onSelected: (JournalProfile) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        Text("Journal template", style = MaterialTheme.typography.titleMedium)
        JournalProfile.entries.forEach { profile ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp)
                    .selectable(selected == profile, role = Role.RadioButton, onClick = { onSelected(profile) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == profile, onClick = null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(profile.title, style = MaterialTheme.typography.bodyLarge)
                    Text(profile.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
