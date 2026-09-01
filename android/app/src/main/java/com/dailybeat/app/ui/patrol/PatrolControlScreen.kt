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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.SuccessGreen

@Composable
fun PatrolControlScreen(
    state: PatrolGridUiState,
    onSelectTab: (SupervisorMissionTab) -> Unit,
    onAssignPatrol: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("patrol_control_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Patrol Control", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Missions, exceptions, and field context",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SupervisorTabs(selected = state.supervisorTab, onSelected = onSelectTab)
        }

        when (state.supervisorTab) {
            SupervisorMissionTab.ACTIVE -> {
                item {
                    Button(
                        onClick = onAssignPatrol,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("assign_patrol"),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Icon(Icons.Default.AddModerator, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Assign patrol")
                    }
                }
                item {
                    SectionHeading("Needs attention", "2 items")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            AttentionRow(
                                warning = true,
                                title = "Night patrol · Sector 4",
                                detail = if (state.trackingActive) "Priority location currently in progress" else "Priority point overdue",
                                unit = "Unit 12",
                                updated = state.primaryMission?.lastUpdateLabel ?: "8m ago",
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            AttentionRow(
                                warning = false,
                                title = "Foot patrol · Market corridor",
                                detail = "Operational deviation · Festival crowd",
                                unit = "Unit 7",
                                updated = "3m ago",
                            )
                        }
                    }
                }

                item { SectionHeading("Active patrols", "Live mission view") }
                items(state.activeMissions, key = { it.id }) { mission ->
                    ActiveMissionCard(mission)
                }

                state.primaryMission?.let { mission ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SectionHeading("Mission map", mission.title)
                            PatrolRouteMap(
                                trackingActive = state.trackingActive,
                                visitedPriorityCount = mission.priorityLocations.count {
                                    it.state == com.dailybeat.app.data.model.PriorityLocationState.VISITED
                                },
                            )
                            Text(
                                text = if (state.recordedTrackPoints > 0) {
                                    "${state.recordedTrackPoints} encrypted route points recorded for this mission"
                                } else {
                                    "Route evidence appears after the assigned patrol starts"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

            }

            SupervisorMissionTab.NEEDS_REVIEW -> {
                item {
                    state.primaryMission?.let { mission ->
                        ReviewPanel(
                            title = mission.title,
                            summary = state.review?.summary ?: "Evidence is being prepared",
                        )
                    }
                }
                item {
                    FairReviewNote()
                }
            }

            SupervisorMissionTab.UPCOMING -> {
                state.upcomingMission?.let { mission ->
                    item {
                        UpcomingMissionRow(
                            title = mission.title,
                            dutyWindow = mission.dutyWindow,
                            assignment = if (mission.personnelCount > 0) {
                                "${mission.unitName} · ${mission.personnelCount} personnel · ${mission.context}"
                            } else {
                                "Unassigned · briefing pending"
                            },
                        )
                    }
                }
                item {
                    UpcomingMissionRow(
                        title = "Evening patrol · Transit hub",
                        dutyWindow = "17:00–21:00",
                        assignment = "Unit 9 · 3 personnel",
                    )
                }
                item {
                    Button(
                        onClick = onAssignPatrol,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text("Assign patrol")
                    }
                }
            }
        }
    }
}

@Composable
private fun SupervisorTabs(
    selected: SupervisorMissionTab,
    onSelected: (SupervisorMissionTab) -> Unit,
) {
    val tabs = listOf(
        SupervisorMissionTab.ACTIVE to "Active",
        SupervisorMissionTab.NEEDS_REVIEW to "Needs review",
        SupervisorMissionTab.UPCOMING to "Upcoming",
    )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.primary,
        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outline) },
    ) {
        tabs.forEach { (tab, label) ->
            Tab(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                text = { Text(label, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun SectionHeading(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AttentionRow(
    warning: Boolean,
    title: String,
    detail: String,
    unit: String,
    updated: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (warning) Icons.Default.WarningAmber else Icons.Default.Info,
            contentDescription = null,
            tint = if (warning) Gold else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (warning) Color(0xFFB66F00) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(unit, style = MaterialTheme.typography.labelMedium)
            Text(updated, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "Open mission",
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActiveMissionCard(mission: PatrolMission) {
    val accent = when (mission.status) {
        PatrolMissionStatus.ACTIVE -> SuccessGreen
        PatrolMissionStatus.PAUSED_WITH_REASON -> MaterialTheme.colorScheme.primary
        PatrolMissionStatus.NEEDS_REVIEW -> Gold
        PatrolMissionStatus.COMPLETED -> SuccessGreen
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row {
            Box(Modifier.background(accent).size(width = 4.dp, height = 126.dp))
            Column(
                modifier = Modifier.padding(14.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (mission.title.startsWith("Foot")) {
                            Icons.AutoMirrored.Filled.DirectionsWalk
                        } else {
                            Icons.Default.LocalPolice
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mission.title, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(mission.dutyWindow, style = MaterialTheme.typography.bodySmall)
                            Text(
                                mission.statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, "Open mission", modifier = Modifier.size(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("${mission.unitName} · ${mission.personnelCount} personnel", style = MaterialTheme.typography.bodySmall)
                }
                Text(mission.context, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    Modifier
                        .fillMaxWidth(if (mission.status == PatrolMissionStatus.ACTIVE) 0.68f else 0.48f)
                        .height(3.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(accent),
                )
            }
        }
    }
}

@Composable
private fun ReviewPanel(title: String, summary: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Gold)
                Text("No automatic staff score is created", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FairReviewNote() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Review with context", style = MaterialTheme.typography.titleMedium)
                Text(
                    "GPS gaps, emergencies, safety decisions, and crowd conditions must be discussed before closing a patrol review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UpcomingMissionRow(title: String, dutyWindow: String, assignment: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(dutyWindow, style = MaterialTheme.typography.bodyMedium)
                Text(assignment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
