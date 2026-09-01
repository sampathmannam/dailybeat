package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.ui.theme.DailyBeatTheme

private data class PatrolNavItem(
    val section: PatrolSection,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val testTag: String,
)

@Composable
fun PatrolGridAppScaffold(
    viewModel: PatrolGridViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DailyBeatTheme(darkTheme = state.role == PatrolRole.PATROL) {
        PatrolGridScaffoldContent(
            state = state,
            onSelectSection = viewModel::selectSection,
            onSelectSupervisorTab = viewModel::selectSupervisorTab,
            onRoleSelected = viewModel::setRole,
            onStartPatrol = viewModel::startPatrol,
            onMarkVisited = viewModel::markCurrentPriorityVisited,
            onAddObservation = viewModel::addObservation,
            onRecordDeviation = viewModel::recordDeviation,
            onEndPatrol = viewModel::endPatrol,
            onAssignPatrol = viewModel::assignPatrol,
            onDismissAssignment = viewModel::dismissAssignment,
            onSaveAssignment = viewModel::saveAssignment,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatrolGridScaffoldContent(
    state: PatrolGridUiState,
    onSelectSection: (PatrolSection) -> Unit,
    onSelectSupervisorTab: (SupervisorMissionTab) -> Unit,
    onRoleSelected: (PatrolRole) -> Unit,
    onStartPatrol: () -> Unit,
    onMarkVisited: () -> Unit,
    onAddObservation: () -> Unit,
    onRecordDeviation: () -> Unit,
    onEndPatrol: () -> Unit,
    onAssignPatrol: () -> Unit,
    onDismissAssignment: () -> Unit,
    onSaveAssignment: (PatrolAssignmentDraft) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.messageId) {
        if (state.messageId > 0 && state.message != null) {
            snackbarHostState.showSnackbar(state.message)
        }
    }
    val navItems = if (state.role == PatrolRole.SUPERVISOR) {
        listOf(
            PatrolNavItem(PatrolSection.PRIMARY, "Control", Icons.Filled.Security, Icons.Outlined.Security, "nav_control"),
            PatrolNavItem(
                PatrolSection.MISSIONS,
                "Missions",
                Icons.AutoMirrored.Filled.Assignment,
                Icons.AutoMirrored.Outlined.Assignment,
                "nav_missions",
            ),
            PatrolNavItem(PatrolSection.UNITS, "Units", Icons.Filled.Groups, Icons.Outlined.Groups, "nav_units"),
            PatrolNavItem(PatrolSection.ALERTS, "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications, "nav_alerts"),
            PatrolNavItem(PatrolSection.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_more"),
        )
    } else {
        listOf(
            PatrolNavItem(PatrolSection.PRIMARY, "My Patrol", Icons.Filled.Security, Icons.Outlined.Security, "nav_my_patrol"),
            PatrolNavItem(
                PatrolSection.MISSIONS,
                "Missions",
                Icons.AutoMirrored.Filled.Assignment,
                Icons.AutoMirrored.Outlined.Assignment,
                "nav_missions",
            ),
            PatrolNavItem(
                PatrolSection.MESSAGES,
                "Messages",
                Icons.AutoMirrored.Filled.Chat,
                Icons.AutoMirrored.Outlined.Chat,
                "nav_messages",
            ),
            PatrolNavItem(PatrolSection.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_more"),
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { PatrolGridTopBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                navItems.forEach { item ->
                    val selected = state.selectedSection == item.section
                    NavigationBarItem(
                        modifier = Modifier.testTag(item.testTag),
                        selected = selected,
                        onClick = { onSelectSection(item.section) },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(item.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        when (state.selectedSection) {
            PatrolSection.PRIMARY -> if (state.role == PatrolRole.SUPERVISOR) {
                PatrolControlScreen(
                    state = state,
                    onSelectTab = onSelectSupervisorTab,
                    onAssignPatrol = onAssignPatrol,
                    modifier = Modifier.padding(innerPadding),
                )
            } else {
                MyPatrolScreen(
                    state = state,
                    onStartPatrol = onStartPatrol,
                    onMarkVisited = onMarkVisited,
                    onAddObservation = onAddObservation,
                    onRecordDeviation = onRecordDeviation,
                    onEndPatrol = onEndPatrol,
                    modifier = Modifier.padding(innerPadding),
                )
            }
            PatrolSection.MORE -> PatrolRoleScreen(
                role = state.role,
                onRoleSelected = onRoleSelected,
                modifier = Modifier.padding(innerPadding),
            )
            else -> PatrolPlaceholderScreen(
                section = state.selectedSection,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (state.assignmentEditorOpen) {
        PatrolAssignmentSheet(
            routePlans = state.routePlans,
            unitOptions = state.unitOptions,
            onDismiss = onDismissAssignment,
            onAssign = onSaveAssignment,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatrolGridTopBar() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Patrol") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)) {
                        append("Grid")
                    }
                },
                style = MaterialTheme.typography.titleLarge,
            )
        },
    )
}

@Composable
private fun PatrolRoleScreen(
    role: PatrolRole,
    onRoleSelected: (PatrolRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineLarge)
        if (BuildConfig.DEBUG) {
            Text(
                "Role preview",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Development build only. Production role and subdivision access must come from secure sign-in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PatrolRoleOption(
                title = "Patrol personnel",
                description = "Mission briefing, priority locations, and bounded patrol tracking",
                selected = role == PatrolRole.PATROL,
                onClick = { onRoleSelected(PatrolRole.PATROL) },
            )
            PatrolRoleOption(
                title = "Supervisor",
                description = "Assignment, active missions, exceptions, and human review",
                selected = role == PatrolRole.SUPERVISOR,
                onClick = { onRoleSelected(PatrolRole.SUPERVISOR) },
            )
        } else {
            Text("Access", style = MaterialTheme.typography.titleLarge)
            Text(
                "This installation is configured for ${if (role == PatrolRole.SUPERVISOR) "supervisor" else "patrol personnel"} access. Role switching is disabled in release builds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "Tracking is off outside an active patrol. PatrolGrid does not create employee rankings or automatic misconduct findings.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PatrolRoleOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PatrolPlaceholderScreen(section: PatrolSection, modifier: Modifier = Modifier) {
    val title = when (section) {
        PatrolSection.MISSIONS -> "Missions"
        PatrolSection.UNITS -> "Units"
        PatrolSection.ALERTS -> "Alerts"
        PatrolSection.MESSAGES -> "Messages"
        else -> "PatrolGrid"
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("placeholder_${section.name.lowercase()}")
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(
            "This workflow is prepared for the next PatrolGrid build slice.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
