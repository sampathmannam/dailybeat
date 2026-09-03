package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.pluralStringResource
import com.dailybeat.app.R
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.PatrolMission
import com.dailybeat.app.data.model.PatrolEndReason
import com.dailybeat.app.data.model.PatrolMissionStatus
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.data.model.SupervisorReviewOutcome
import com.dailybeat.app.patrolgrid.PatrolEvidenceSource
import com.dailybeat.app.patrolgrid.PatrolPriorityVisitEvidence
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class PatrolFieldUpdateKind(
    val title: String,
    val prompt: String,
    val support: String,
    val testTag: String,
) {
    OBSERVATION(
        title = "Add observation",
        prompt = "What did you observe?",
        support = "Record clear operational facts that may help the patrol record.",
        testTag = "observation_detail",
    ),
    DEVIATION(
        title = "Record deviation",
        prompt = "Why did the patrol plan change?",
        support = "Safety, emergencies, crowd conditions, and operational directions are valid context.",
        testTag = "deviation_detail",
    ),
    SAFETY_EVENT(
        title = "Record safety event",
        prompt = "What safety issue occurred?",
        support = "This creates supervisor context; it does not replace emergency radio or phone procedures.",
        testTag = "safety_detail",
    ),
    REVIEW_CONTEXT(
        title = "Respond to review",
        prompt = "Add the context requested by your supervisor",
        support = "Record the operational facts you remember. This response becomes part of the review record.",
        testTag = "review_context_detail",
    ),
}

@Composable
fun PatrolFieldUpdateDialog(
    kind: PatrolFieldUpdateKind,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var detail by remember(kind) { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("field_update_dialog"),
        onDismissRequest = onDismiss,
        title = { Text(kind.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(kind.support, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth().testTag(kind.testTag),
                    label = { Text(kind.prompt) },
                    minLines = 3,
                    maxLines = 7,
                    supportingText = { Text("${detail.length}/4000") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(detail.trim()) },
                enabled = detail.isNotBlank(),
                modifier = Modifier.testTag("save_field_update"),
            ) {
                Text("Save securely")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EndPatrolConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: (PatrolEndReason) -> Unit,
) {
    var reason by remember { mutableStateOf(PatrolEndReason.COMPLETED) }
    AlertDialog(
        modifier = Modifier.testTag("end_patrol_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("End this patrol?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Location tracking stops immediately. Encrypted evidence already on this device will continue synchronizing securely.",
                )
                PatrolEndReason.entries
                    .filterNot { it == PatrolEndReason.DUTY_WINDOW_ENDED }
                    .forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == option,
                                onClick = { reason = option },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = null)
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) }, modifier = Modifier.testTag("confirm_end_patrol")) {
                Text("End patrol")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continue patrol") } },
    )
}

@Composable
fun PatrolEvidenceSourceSelector(
    sources: List<PatrolEvidenceSource>,
    selectedSessionId: String?,
    loading: Boolean,
    error: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = sources.firstOrNull { it.sessionId == selectedSessionId }
    Column(
        modifier = modifier.fillMaxWidth().testTag("evidence_source_selector"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Evidence source", style = MaterialTheme.typography.titleLarge)
        Text(
            "Each route below belongs to one person and one patrol session. Trails are never combined.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sources.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("evidence_source_empty"),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "No separately attributable GPS session is available for this mission.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            sources.forEach { source ->
                val isSelected = source.sessionId == selectedSessionId
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(source.sessionId) },
                            role = Role.RadioButton,
                        )
                        .testTag("evidence_source_${source.sessionId}"),
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                buildString {
                                    append(source.displayName)
                                    source.badgeNumber?.let { append(" · ").append(it) }
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${source.trackPointCount} server-received GPS points · " +
                                    formatEvidenceTime(source.startedAtMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        if (loading) {
            Text(
                "Loading the selected session…",
                modifier = Modifier.testTag("evidence_source_loading"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let {
            Text(
                it,
                modifier = Modifier.testTag("evidence_source_error"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        selected?.let { source ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("selected_evidence_provenance"),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("Session provenance", style = MaterialTheme.typography.titleMedium)
                    Text("Started ${formatEvidenceTime(source.startedAtMs)}")
                    Text(
                        source.endedAtMs?.let {
                            "Ended ${formatEvidenceTime(it)} · ${formatEvidenceValue(source.endReason ?: "closed")}"
                        } ?: "Session is still in progress",
                    )
                    Text("PatrolGrid app ${source.appVersion}")
                    if (source.firstRecordedAtMs != null && source.lastRecordedAtMs != null) {
                        Text(
                            "Device-recorded span ${formatEvidenceTime(source.firstRecordedAtMs)} – " +
                                formatEvidenceTime(source.lastRecordedAtMs),
                        )
                    }
                    source.lastReceivedAtMs?.let {
                        Text("Last received by server ${formatEvidenceTime(it)}")
                    }
                    if (source.bestAccuracyM != null && source.worstAccuracyM != null) {
                        Text(
                            "Reported horizontal accuracy ${source.bestAccuracyM.roundToInt()}–" +
                                "${source.worstAccuracyM.roundToInt()} m",
                        )
                    }
                    Text(
                        "Accuracy is the phone's reported estimate, not proof by itself. " +
                            "A supervisor makes the final operational assessment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolMissionDetailSheet(
    mission: PatrolMission,
    state: PatrolGridUiState,
    onDismiss: () -> Unit,
    onSelectEvidenceSource: (String) -> Unit,
    onReview: () -> Unit,
) {
    val selectedEvidenceAvailable = state.evidenceMissionId == mission.id
    val selectedEvidenceSource = state.evidenceSources.firstOrNull {
        it.sessionId == state.selectedEvidenceSessionId
    }
    val selectedTrackPointCount = state.selectedEvidenceTrackPointCount
    val selectedPriorityVisitEvidence = state.priorityVisitEvidence.filter {
        it.sessionId == state.selectedEvidenceSessionId
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("mission_detail_sheet"),
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding().testTag("mission_detail_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(mission.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${mission.dutyWindow} · ${mission.statusLabel}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "${mission.unitName} · ${mission.personnelCount} personnel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(mission.context, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.reviewContextRequest?.let { request ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("review_context_request"),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Supervisor requested context", style = MaterialTheme.typography.titleMedium)
                            Text(request, style = MaterialTheme.typography.bodyMedium)
                            state.reviewContextResponse?.let { response ->
                                Text("Field response", style = MaterialTheme.typography.labelLarge)
                                Text(response, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (selectedEvidenceAvailable && state.serverBacked) {
                item {
                    PatrolEvidenceSourceSelector(
                        sources = state.evidenceSources,
                        selectedSessionId = state.selectedEvidenceSessionId,
                        loading = state.evidenceTrailLoading,
                        error = state.evidenceTrailError,
                        onSelect = onSelectEvidenceSource,
                    )
                }
            }
            if (selectedEvidenceAvailable) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Planned and recorded route", style = MaterialTheme.typography.titleLarge)
                        if (state.evidenceTrailLoading) {
                            Text(
                                "Loading this session's route trail…",
                                modifier = Modifier.testTag("evidence_trail_loading"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            PatrolRouteMap(
                                trackingActive = state.trackingActive,
                                visitedPriorityCount = mission.priorityLocations.count {
                                    it.state == PriorityLocationState.VISITED
                                },
                                recordedPoints = state.routePoints,
                                plannedPoints = state.plannedRoutePoints,
                                priorityLocations = mission.priorityLocations,
                                totalRecordedPoints = selectedTrackPointCount,
                                demoMode = !state.serverBacked &&
                                    !state.trackingActive &&
                                    state.recordedTrackPoints == 0 &&
                                    state.unreadableTrackPoints == 0 &&
                                    state.routePoints.isEmpty() &&
                                    state.plannedRoutePoints.isEmpty(),
                            )
                        }
                        PatrolEvidenceIntegrityNotice(
                            unreadableTrackPoints = state.unreadableTrackPoints,
                            captureError = state.captureError,
                        )
                        Text(
                            run {
                                val points = pluralStringResource(
                                    R.plurals.patrolgrid_route_points,
                                    state.recordedTrackPoints,
                                    state.recordedTrackPoints,
                                )
                                val observations = pluralStringResource(
                                    R.plurals.patrolgrid_observations,
                                    state.observationCount,
                                    state.observationCount,
                                )
                                if (selectedEvidenceSource == null) {
                                    "$points · $observations"
                                } else {
                                    val sessionPoints = pluralStringResource(
                                        R.plurals.patrolgrid_session_points,
                                        state.selectedEvidenceTrackPointCount,
                                        state.selectedEvidenceTrackPointCount,
                                    )
                                    "$sessionPoints · ${state.recordedTrackPoints} across the mission · " +
                                        observations
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else if (state.serverBacked) {
                item {
                    Text(
                        if (state.refreshError == null) {
                            "Loading route evidence for this mission…"
                        } else {
                            "Route evidence is unavailable. Use Retry above to try again."
                        },
                        modifier = Modifier.testTag("mission_evidence_loading"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { Text("Priority locations", style = MaterialTheme.typography.titleLarge) }
            if (mission.priorityLocations.isEmpty()) {
                item {
                    Text(
                        "No priority locations are attached to this mission.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(mission.priorityLocations, key = { it.id }) { priority ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(priority.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            priority.detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.serverBacked && selectedEvidenceSource != null) {
                item {
                    PatrolPriorityVisitEvidenceList(selectedPriorityVisitEvidence)
                }
            }
            if (state.role == com.dailybeat.app.data.model.PatrolRole.SUPERVISOR &&
                mission.status == PatrolMissionStatus.NEEDS_REVIEW
            ) {
                item {
                    Button(
                        onClick = onReview,
                        enabled = !state.operationInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("review_mission"),
                    ) {
                        Text("Record supervisor review")
                    }
                }
            }
        }
    }
}

@Composable
private fun PatrolPriorityVisitEvidenceList(
    visits: List<PatrolPriorityVisitEvidence>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag("priority_visit_evidence"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Visits in this session", style = MaterialTheme.typography.titleLarge)
        if (visits.isEmpty()) {
            Text(
                "No priority visit evidence was recorded in this session.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        visits.forEach { visit ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("priority_visit_${visit.priorityLocationId}"),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(visit.priorityName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatVisitMethod(visit.method)} · ${visit.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Occurred ${formatEvidenceTime(visit.visitedAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Received by server ${formatEvidenceTime(visit.receivedAtMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    visit.accuracyM?.let {
                        Text(
                            "Phone-reported accuracy ${it.roundToInt()} m",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    visit.note?.let {
                        Text("Context: $it", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun PatrolReviewDialog(
    mission: PatrolMission,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (SupervisorReviewOutcome, String) -> Unit,
) {
    var outcome by remember { mutableStateOf(SupervisorReviewOutcome.APPROVED) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("review_dialog"),
        onDismissRequest = onDismiss,
        title = { Text("Review ${mission.title}") },
        text = {
            // Three outcome options plus a multi-line notes field overflow the space left
            // above the keyboard, which pushed Cancel and Submit underneath it. Scrolling
            // the content keeps the dialog's own buttons reachable while typing.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReviewOutcomeOption(
                    title = "Approved",
                    description = "Evidence and field context are sufficient to close the review.",
                    selected = outcome == SupervisorReviewOutcome.APPROVED,
                    onClick = { outcome = SupervisorReviewOutcome.APPROVED },
                )
                ReviewOutcomeOption(
                    title = "Needs context",
                    description = "Keep the review open and request a specific explanation.",
                    selected = outcome == SupervisorReviewOutcome.NEEDS_CONTEXT,
                    onClick = { outcome = SupervisorReviewOutcome.NEEDS_CONTEXT },
                )
                ReviewOutcomeOption(
                    title = "Technically inconclusive",
                    description = "Close without a staff finding because the evidence is insufficient.",
                    selected = outcome == SupervisorReviewOutcome.TECHNICALLY_INCONCLUSIVE,
                    onClick = { outcome = SupervisorReviewOutcome.TECHNICALLY_INCONCLUSIVE },
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(4_000) },
                    modifier = Modifier.fillMaxWidth().testTag("review_notes"),
                    label = { Text("Review notes") },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = { Text("Required · ${notes.length}/4000") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(outcome, notes.trim()) },
                enabled = !submitting && notes.isNotBlank(),
                modifier = Modifier.testTag("submit_review"),
            ) {
                Text(if (submitting) "Submitting…" else "Submit review")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("Cancel") } },
    )
}

@Composable
private fun ReviewOutcomeOption(
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
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val EVIDENCE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm")

private fun formatEvidenceTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .format(EVIDENCE_TIME_FORMATTER)

private fun formatEvidenceValue(value: String): String = value
    .trim()
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun formatVisitMethod(method: String): String = when (method) {
    "gps" -> "GPS visit"
    "manual_with_context" -> "Manual visit with context"
    else -> "Recorded visit"
}
