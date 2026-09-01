package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.SuccessGreen

@Composable
fun MyPatrolScreen(
    state: PatrolGridUiState,
    onStartPatrol: () -> Unit,
    onMarkVisited: () -> Unit,
    onAddObservation: () -> Unit,
    onRecordDeviation: () -> Unit,
    onEndPatrol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mission = state.primaryMission ?: return
    val visitedCount = mission.priorityLocations.count { it.state == PriorityLocationState.VISITED }
    LazyColumn(
        modifier = modifier.testTag("my_patrol_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("My Patrol", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Your assigned mission and field actions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            MissionBriefingCard(
                mission = mission,
                trackingActive = state.trackingActive,
                locationPermissionGranted = state.locationPermissionGranted,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Priority locations", style = MaterialTheme.typography.titleLarge)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column {
                        mission.priorityLocations.forEachIndexed { index, location ->
                            PriorityLocationRow(
                                number = index + 1,
                                location = location,
                                enabled = state.trackingActive && location.state == PriorityLocationState.CURRENT,
                                onClick = onMarkVisited,
                            )
                            if (index != mission.priorityLocations.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
                if (state.trackingActive && mission.priorityLocations.any { it.state == PriorityLocationState.CURRENT }) {
                    Text(
                        "Tap the current location after completing the visit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text("Route context", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (state.recordedTrackPoints > 0) "${state.recordedTrackPoints} points" else "Waiting to start",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PatrolRouteMap(
                    trackingActive = state.trackingActive,
                    visitedPriorityCount = visitedCount,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Field judgment comes first", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "The suggested route may change for safety or operational need. Record the reason when practical.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            if (state.trackingActive) {
                ActivePatrolActions(
                    onAddObservation = onAddObservation,
                    onRecordDeviation = onRecordDeviation,
                    onEndPatrol = onEndPatrol,
                )
            } else {
                Button(
                    onClick = onStartPatrol,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("start_patrol"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF0F172A),
                    ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Start patrol", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MissionBriefingCard(
    mission: PatrolMission,
    trackingActive: Boolean,
    locationPermissionGranted: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.LocalPolice, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(mission.title, style = MaterialTheme.typography.titleLarge)
                Text(mission.dutyWindow, style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(if (trackingActive) SuccessGreen else Gold, CircleShape),
                    )
                    Text(
                        when {
                            trackingActive && locationPermissionGranted -> "Tracking active"
                            trackingActive -> "Patrol active · location unavailable"
                            else -> mission.statusLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (trackingActive && locationPermissionGranted) {
                            SuccessGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Offline ready", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PriorityLocationRow(
    number: Int,
    location: PriorityLocation,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val statusColor = when (location.state) {
        PriorityLocationState.VISITED -> SuccessGreen
        PriorityLocationState.CURRENT -> Color(0xFF4EA5FF)
        PriorityLocationState.REMAINING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (location.state == PriorityLocationState.VISITED) {
                Icon(Icons.Default.Check, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
            } else {
                Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
        }
        Text(location.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(location.detail, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        if (enabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Mark ${location.name} visited",
                modifier = Modifier.size(14.dp),
                tint = statusColor,
            )
        }
    }
}

@Composable
private fun ActivePatrolActions(
    onAddObservation: () -> Unit,
    onRecordDeviation: () -> Unit,
    onEndPatrol: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onAddObservation,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(6.dp))
                Text("Observation")
            }
            OutlinedButton(
                onClick = onRecordDeviation,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(6.dp))
                Text("Deviation")
            }
        }
        Button(
            onClick = onEndPatrol,
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("end_patrol"),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0F172A)),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("End patrol", fontWeight = FontWeight.SemiBold)
        }
    }
}
