package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.PatrolAssignmentDraft
import com.dailybeat.app.data.model.PatrolRouteGuidance
import com.dailybeat.app.data.model.PatrolRoutePlan
import com.dailybeat.app.data.model.PatrolUnitOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolAssignmentSheet(
    routePlans: List<PatrolRoutePlan>,
    unitOptions: List<PatrolUnitOption>,
    onDismiss: () -> Unit,
    onAssign: (PatrolAssignmentDraft) -> Unit,
    assigning: Boolean = false,
) {
    if (routePlans.isEmpty() || unitOptions.isEmpty()) return
    var selectedRouteId by rememberSaveable { mutableStateOf(routePlans.first().id) }
    var selectedUnitName by rememberSaveable { mutableStateOf(unitOptions.first().name) }
    var selectedGuidance by rememberSaveable { mutableStateOf(PatrolRouteGuidance.SUGGESTED_ROUTE) }
    val selectedUnit = unitOptions.first { it.name == selectedUnitName }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("assignment_sheet"),
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .testTag("assignment_list"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Assign patrol", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Choose the mission, team, and how much route flexibility field personnel have.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AssignmentHeading("Route and duty window")
            routePlans.forEach { route ->
                AssignmentOption(
                    title = route.title,
                    description = "${route.dutyWindow} · ${route.priorityLocations.joinToString()}",
                    selected = route.id == selectedRouteId,
                    testTag = "route_${route.id.replace('-', '_')}",
                    onClick = { selectedRouteId = route.id },
                )
            }

            AssignmentHeading("Patrol unit")
            unitOptions.forEach { unit ->
                AssignmentOption(
                    title = unit.name,
                    description = "${unit.personnelCount} personnel",
                    selected = unit.name == selectedUnitName,
                    testTag = "unit_${unit.name.substringAfter(' ').lowercase()}",
                    leadingIcon = { Icon(Icons.Default.Group, contentDescription = null) },
                    onClick = { selectedUnitName = unit.name },
                )
            }

            AssignmentHeading("Route guidance")
            AssignmentOption(
                title = "Suggested route",
                description = "Recommended for routine patrol. Field judgment and documented deviations remain allowed.",
                selected = selectedGuidance == PatrolRouteGuidance.SUGGESTED_ROUTE,
                testTag = "guidance_suggested",
                leadingIcon = { Icon(Icons.Default.Route, contentDescription = null) },
                onClick = { selectedGuidance = PatrolRouteGuidance.SUGGESTED_ROUTE },
            )
            AssignmentOption(
                title = "Flexible area coverage",
                description = "Set priorities and boundaries without prescribing a street-by-street route.",
                selected = selectedGuidance == PatrolRouteGuidance.AREA_COVERAGE,
                testTag = "guidance_area",
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null) },
                onClick = { selectedGuidance = PatrolRouteGuidance.AREA_COVERAGE },
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "PatrolGrid records evidence and exceptions for human review. This assignment does not create a compliance score.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = {
                    onAssign(
                        PatrolAssignmentDraft(
                            routePlanId = selectedRouteId,
                            unitName = selectedUnit.name,
                            personnelCount = selectedUnit.personnelCount,
                            guidance = selectedGuidance,
                            unitId = selectedUnit.id,
                        ),
                    )
                },
                enabled = !assigning,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("confirm_assignment"),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (assigning) "Assigning…" else "Assign mission", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AssignmentHeading(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AssignmentOption(
    title: String,
    description: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.invoke()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}
