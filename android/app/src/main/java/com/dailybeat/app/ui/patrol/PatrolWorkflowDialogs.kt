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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolMissionDetailSheet(
    mission: PatrolMission,
    state: PatrolGridUiState,
    onDismiss: () -> Unit,
    onReview: () -> Unit,
) {
    val selectedEvidenceAvailable = state.evidenceMissionId == mission.id
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
            if (selectedEvidenceAvailable) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Planned and recorded route", style = MaterialTheme.typography.titleLarge)
                        PatrolRouteMap(
                            trackingActive = state.trackingActive,
                            visitedPriorityCount = mission.priorityLocations.count {
                                it.state == PriorityLocationState.VISITED
                            },
                            recordedPoints = state.routePoints,
                            plannedPoints = state.plannedRoutePoints,
                            priorityLocations = mission.priorityLocations,
                            totalRecordedPoints = state.recordedTrackPoints,
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
                        Text(
                            "${state.recordedTrackPoints} route points · ${state.observationCount} observations",
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
