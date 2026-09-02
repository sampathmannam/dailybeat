package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import com.dailybeat.app.patrolgrid.PATROLGRID_RETENTION_INCIDENT_MESSAGE
import com.dailybeat.app.ui.theme.DailyBeatTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private data class PatrolNavItem(
    val section: PatrolSection,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val testTag: String,
)

internal fun shouldUsePatrolNavigationRail(widthDp: Float): Boolean = widthDp >= 720f

@Composable
fun PatrolGridAppScaffold(
    viewModelKey: String = "patrolgrid-local",
    viewModel: PatrolGridViewModel = viewModel(key = viewModelKey),
    onSignedOut: () -> Unit = {},
    onSessionExpired: () -> Unit = {},
    onLocked: () -> Unit = {},
    onRequestLocationPermission: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
                viewModel.refreshFromForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.deactivateRouteObservation()
        }
    }
    LaunchedEffect(state.serverBacked, state.role) {
        if (!state.serverBacked || state.role != PatrolRole.SUPERVISOR) return@LaunchedEffect
        while (isActive) {
            delay(30_000)
            viewModel.refreshFromForeground()
        }
    }
    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) onSessionExpired()
    }
    DailyBeatTheme {
        PatrolGridScaffoldContent(
            state = state,
            onSelectSection = viewModel::selectSection,
            onSelectSupervisorTab = viewModel::selectSupervisorTab,
            onRoleSelected = viewModel::setRole,
            onStartPatrol = {
                if (!state.locationPermissionGranted) {
                    onRequestLocationPermission()
                } else {
                    viewModel.startPatrol()
                }
            },
            onMarkVisited = viewModel::markCurrentPriorityVisited,
            onAddObservation = viewModel::addObservation,
            onRecordDeviation = viewModel::recordDeviation,
            onRecordSafetyEvent = viewModel::recordSafetyEvent,
            onRecordReviewContext = viewModel::recordReviewContext,
            onEndPatrol = viewModel::endPatrol,
            onAssignPatrol = viewModel::assignPatrol,
            onDismissAssignment = viewModel::dismissAssignment,
            onSaveAssignment = viewModel::saveAssignment,
            onLock = { viewModel.lockApp(onLocked) },
            onSignOut = { viewModel.signOut(onSignedOut) },
            onRetry = viewModel::retryRefresh,
            onOpenMission = viewModel::openMission,
            onDismissMission = viewModel::dismissMissionDetails,
            onSubmitReview = viewModel::submitReview,
            onAcknowledgeRetentionIncident = viewModel::acknowledgeRetentionIncident,
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
    onAddObservation: (String) -> Unit,
    onRecordDeviation: (String) -> Unit,
    onRecordSafetyEvent: (String) -> Unit,
    onRecordReviewContext: (String) -> Unit,
    onEndPatrol: (PatrolEndReason) -> Unit,
    onAssignPatrol: () -> Unit,
    onDismissAssignment: () -> Unit,
    onSaveAssignment: (PatrolAssignmentDraft) -> Unit,
    onLock: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    onOpenMission: (String) -> Unit,
    onDismissMission: () -> Unit,
    onSubmitReview: (SupervisorReviewOutcome, String) -> Unit,
    onAcknowledgeRetentionIncident: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var fieldEditor by remember { mutableStateOf<PatrolFieldUpdateKind?>(null) }
    var endConfirmationOpen by remember { mutableStateOf(false) }
    var reviewEditorOpen by remember { mutableStateOf(false) }
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
            PatrolNavItem(PatrolSection.MORE, "More", Icons.Filled.MoreHoriz, Icons.Outlined.MoreHoriz, "nav_more"),
        )
    }

    val sectionContent: @Composable (Modifier) -> Unit = { sectionModifier ->
        when (state.selectedSection) {
            PatrolSection.PRIMARY -> if (state.role == PatrolRole.SUPERVISOR) {
                PatrolControlScreen(
                    state = state,
                    onSelectTab = onSelectSupervisorTab,
                    onAssignPatrol = onAssignPatrol,
                    onOpenMission = onOpenMission,
                    modifier = sectionModifier,
                )
            } else {
                MyPatrolScreen(
                    state = state,
                    onStartPatrol = onStartPatrol,
                    onMarkVisited = onMarkVisited,
                    onAddObservation = { fieldEditor = PatrolFieldUpdateKind.OBSERVATION },
                    onRecordDeviation = { fieldEditor = PatrolFieldUpdateKind.DEVIATION },
                    onRecordSafetyEvent = { fieldEditor = PatrolFieldUpdateKind.SAFETY_EVENT },
                    onRecordReviewContext = { fieldEditor = PatrolFieldUpdateKind.REVIEW_CONTEXT },
                    onEndPatrol = { endConfirmationOpen = true },
                    modifier = sectionModifier,
                )
            }
            PatrolSection.MORE -> PatrolRoleScreen(
                role = state.role,
                onRoleSelected = onRoleSelected,
                serverBacked = state.serverBacked,
                subdivisionName = state.subdivisionName,
                pendingActionCount = state.pendingActionCount,
                pendingRoutePointCount = state.pendingRoutePointCount,
                pendingSessionClose = state.pendingSessionClose,
                showingCachedData = state.showingCachedData,
                onLock = onLock,
                onSignOut = onSignOut,
                modifier = sectionModifier,
            )
            PatrolSection.MISSIONS -> PatrolMissionsScreen(
                state = state,
                onOpenMission = onOpenMission,
                modifier = sectionModifier,
            )
            PatrolSection.UNITS -> PatrolUnitsScreen(
                state = state,
                modifier = sectionModifier,
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = shouldUsePatrolNavigationRail(maxWidth.value)
        if (useNavigationRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                PatrolNavigationRail(
                    items = navItems,
                    selectedSection = state.selectedSection,
                    onSelectSection = onSelectSection,
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = { PatrolGridTopBar(state, onRetry) },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        sectionContent(Modifier.widthIn(max = 1_200.dp).fillMaxSize())
                    }
                }
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { PatrolGridTopBar(state, onRetry) },
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    PatrolNavigationBar(
                        items = navItems,
                        selectedSection = state.selectedSection,
                        onSelectSection = onSelectSection,
                    )
                },
            ) { innerPadding ->
                sectionContent(Modifier.fillMaxSize().padding(innerPadding))
            }
        }
    }

    if (state.assignmentEditorOpen) {
        PatrolAssignmentSheet(
            routePlans = state.routePlans,
            unitOptions = state.unitOptions,
            onDismiss = onDismissAssignment,
            onAssign = onSaveAssignment,
            onRetry = onRetry,
            assigning = state.operationInProgress,
        )
    }

    fieldEditor?.let { kind ->
        PatrolFieldUpdateDialog(
            kind = kind,
            onDismiss = { fieldEditor = null },
            onSave = { detail ->
                when (kind) {
                    PatrolFieldUpdateKind.OBSERVATION -> onAddObservation(detail)
                    PatrolFieldUpdateKind.DEVIATION -> onRecordDeviation(detail)
                    PatrolFieldUpdateKind.SAFETY_EVENT -> onRecordSafetyEvent(detail)
                    PatrolFieldUpdateKind.REVIEW_CONTEXT -> onRecordReviewContext(detail)
                }
                fieldEditor = null
            },
        )
    }

    if (endConfirmationOpen) {
        EndPatrolConfirmationDialog(
            onDismiss = { endConfirmationOpen = false },
            onConfirm = { reason ->
                endConfirmationOpen = false
                onEndPatrol(reason)
            },
        )
    }

    val selectedMission = state.allMissions.firstOrNull { it.id == state.selectedMissionId }
    if (state.missionDetailsOpen && selectedMission != null) {
        PatrolMissionDetailSheet(
            mission = selectedMission,
            state = state,
            onDismiss = onDismissMission,
            onReview = { reviewEditorOpen = true },
        )
    }

    if (reviewEditorOpen && selectedMission != null) {
        PatrolReviewDialog(
            mission = selectedMission,
            submitting = state.operationInProgress,
            onDismiss = { reviewEditorOpen = false },
            onSubmit = { outcome, notes ->
                reviewEditorOpen = false
                onSubmitReview(outcome, notes)
            },
        )
    }

    if (state.captureError == PATROLGRID_RETENTION_INCIDENT_MESSAGE) {
        PatrolRetentionIncidentAcknowledgement(onAcknowledgeRetentionIncident)
    }
}

@Composable
internal fun PatrolRetentionIncidentAcknowledgement(onAcknowledge: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag("retention_incident_acknowledgement"),
        onDismissRequest = {},
        title = { Text("Evidence cleanup recorded") },
        text = {
            Text(
                "Expired local evidence was securely removed. Report the evidence-integrity incident to your subdivision supervisor through the official Department channel, then acknowledge it here. Only an aggregate count and time remain on this device.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAcknowledge,
                modifier = Modifier.testTag("acknowledge_retention_incident"),
            ) { Text("Reported and acknowledged") }
        },
    )
}

@Composable
private fun PatrolNavigationBar(
    items: List<PatrolNavItem>,
    selectedSection: PatrolSection,
    onSelectSection: (PatrolSection) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.testTag("patrol_navigation_bar"),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        items.forEach { item ->
            val selected = selectedSection == item.section
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
}

@Composable
private fun PatrolNavigationRail(
    items: List<PatrolNavItem>,
    selectedSection: PatrolSection,
    onSelectSection: (PatrolSection) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.testTag("patrol_navigation_rail"),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        items.forEach { item ->
            val selected = selectedSection == item.section
            NavigationRailItem(
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
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatrolGridTopBar(state: PatrolGridUiState, onRetry: () -> Unit) {
    Column {
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
        if (state.loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.refreshError?.let { error ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("retry_refresh")) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun PatrolRoleScreen(
    role: PatrolRole,
    onRoleSelected: (PatrolRole) -> Unit,
    serverBacked: Boolean,
    subdivisionName: String?,
    pendingActionCount: Int,
    pendingRoutePointCount: Int,
    pendingSessionClose: Boolean,
    showingCachedData: Boolean,
    onLock: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("More", style = MaterialTheme.typography.headlineLarge)
        if (BuildConfig.DEBUG && !serverBacked) {
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
        if (serverBacked) {
            val pendingTotal = pendingActionCount + pendingRoutePointCount + if (pendingSessionClose) 1 else 0
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("sync_status"),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Secure synchronization", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            showingCachedData -> "Offline · securely cached briefing shown"
                            pendingTotal == 0 -> "All patrol evidence is synchronized"
                            else -> "$pendingTotal secure item${if (pendingTotal == 1) "" else "s"} waiting to synchronize"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (serverBacked) {
            Text(
                subdivisionName ?: "Assigned subdivision",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onLock, modifier = Modifier.testTag("lock_app")) {
                Text("Lock app now")
            }
            TextButton(onClick = onSignOut, modifier = Modifier.testTag("sign_out")) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun PatrolMissionsScreen(
    state: PatrolGridUiState,
    onOpenMission: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val missions = state.allMissions
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize().testTag("missions_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Missions", style = MaterialTheme.typography.headlineLarge) }
        if (missions.isEmpty()) {
            item {
                Text(
                    "No missions are available for this account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(missions.size, key = { missions[it].id }) { index ->
                val mission = missions[index]
                Surface(
                    onClick = { onOpenMission(mission.id) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(mission.title, style = MaterialTheme.typography.titleLarge)
                        Text("${mission.dutyWindow} · ${mission.statusLabel}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            mission.context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Open mission",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatrolUnitsScreen(state: PatrolGridUiState, modifier: Modifier = Modifier) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize().testTag("units_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Patrol units", style = MaterialTheme.typography.headlineLarge) }
        if (state.unitOptions.isEmpty()) {
            item {
                Text(
                    "No staffed units are available.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.unitOptions.size, key = { state.unitOptions[it].id }) { index ->
                val unit = state.unitOptions[index]
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(unit.name, style = MaterialTheme.typography.titleMedium)
                        Text("${unit.personnelCount} active personnel", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
