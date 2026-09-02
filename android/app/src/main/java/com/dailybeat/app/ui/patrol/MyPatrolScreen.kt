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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.operationalSuccessColor
import com.dailybeat.app.ui.theme.operationalWarningColor

@Composable
fun MyPatrolScreen(
    state: PatrolGridUiState,
    onStartPatrol: () -> Unit,
    onMarkVisited: () -> Unit,
    onAddObservation: () -> Unit,
    onRecordDeviation: () -> Unit,
    onRecordSafetyEvent: () -> Unit,
    onRecordReviewContext: () -> Unit,
    onEndPatrol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mission = state.primaryMission
    if (mission == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp).testTag("no_assigned_mission"),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalPolice, contentDescription = null, modifier = Modifier.size(40.dp))
                Text("No patrol assigned", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Your next mission will appear here after your supervisor assigns it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }
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
                                enabled = state.trackingActive &&
                                    !state.operationInProgress &&
                                    location.state == PriorityLocationState.CURRENT,
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
                        when {
                            state.selectedEvidenceTrackPointCount > 0 ->
                                "${state.selectedEvidenceTrackPointCount} points"
                            state.trackingActive -> "Tracking active"
                            else -> "Waiting to start"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PatrolRouteMap(
                    trackingActive = state.trackingActive,
                    visitedPriorityCount = visitedCount,
                    recordedPoints = state.routePoints,
                    plannedPoints = state.plannedRoutePoints,
                    priorityLocations = mission.priorityLocations,
                    totalRecordedPoints = state.selectedEvidenceTrackPointCount,
                    demoMode = !state.serverBacked &&
                        !state.trackingActive &&
                        state.recordedTrackPoints == 0 &&
                        state.unreadableTrackPoints == 0 &&
                        state.routePoints.isEmpty() &&
                        state.plannedRoutePoints.isEmpty(),
                )
                PatrolEvidenceIntegrityNotice(
                    unreadableTrackPoints = state.unreadableTrackPoints,
                    captureError = state.captureError,
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
                    enabled = !state.operationInProgress,
                    onAddObservation = onAddObservation,
                    onRecordDeviation = onRecordDeviation,
                    onRecordSafetyEvent = onRecordSafetyEvent,
                    onEndPatrol = onEndPatrol,
                )
            } else if (mission.status == com.dailybeat.app.data.model.PatrolMissionStatus.ASSIGNED) {
                Button(
                    onClick = onStartPatrol,
                    enabled = !state.operationInProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp)
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
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("patrol_closed_state"),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (mission.status == com.dailybeat.app.data.model.PatrolMissionStatus.NEEDS_REVIEW) {
                                    "Patrol ended · waiting for supervisor review"
                                } else {
                                    "This patrol is closed. Tracking remains off."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            state.reviewContextRequest?.let { request ->
                                Text("Context requested: $request", style = MaterialTheme.typography.bodyMedium)
                                state.reviewContextResponse?.let { response ->
                                    Text(
                                        "Response saved: $response",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    if (!state.reviewContextRequest.isNullOrBlank() && state.reviewContextResponse.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = onRecordReviewContext,
                            enabled = !state.operationInProgress,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("respond_review_context"),
                        ) {
                            Text("Respond with context")
                        }
                    }
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
    val context = LocalContext.current
    val successColor = operationalSuccessColor()
    val warningColor = operationalWarningColor()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
    ) {
        Column {
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
                            .background(if (trackingActive) successColor else warningColor, CircleShape),
                    )
                    Text(
                        when {
                            trackingActive && locationPermissionGranted -> "Tracking active"
                            trackingActive -> "Patrol active · location unavailable"
                            else -> mission.statusLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (trackingActive && locationPermissionGranted) {
                            successColor
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (trackingActive) "Encrypted locally" else "Tracking off",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            if (trackingActive && !locationPermissionGranted) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Open app settings")
                }
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
    val successColor = operationalSuccessColor()
    val statusColor = when (location.state) {
        PriorityLocationState.VISITED -> successColor
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
    enabled: Boolean,
    onAddObservation: () -> Unit,
    onRecordDeviation: () -> Unit,
    onRecordSafetyEvent: () -> Unit,
    onEndPatrol: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onAddObservation,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("add_observation"),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.AddComment, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(6.dp))
                Text("Observation")
            }
            OutlinedButton(
                onClick = onRecordDeviation,
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("record_deviation"),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(6.dp))
                Text("Deviation")
            }
        }
        OutlinedButton(
            onClick = onRecordSafetyEvent,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("record_safety_event"),
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.size(6.dp))
            Text("Safety event")
        }
        Button(
            onClick = onEndPatrol,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("end_patrol"),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0F172A)),
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("End patrol", fontWeight = FontWeight.SemiBold)
        }
    }
}
