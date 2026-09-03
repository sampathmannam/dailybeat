package com.dailybeat.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import com.dailybeat.app.data.model.PatrolRole
import com.dailybeat.app.ui.components.PrimaryButton

@Composable
fun OnboardingScreen(
    onComplete: (officerName: String, role: PatrolRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var officerName by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(PatrolRole.PATROL) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("PG", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }

            when (step) {
                0 -> {
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_welcome_body),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.onboarding_continue),
                        onClick = { step = 1 },
                        modifier = Modifier.testTag("onboarding_welcome_continue"),
                    )
                }
                1 -> {
                    Text(
                        text = stringResource(R.string.onboarding_officer_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    OutlinedTextField(
                        value = officerName,
                        // Bound the field the way every other free-text input here is bounded.
                        onValueChange = { officerName = it.take(80) },
                        modifier = Modifier.fillMaxWidth().testTag("onboarding_officer_name"),
                        label = { Text(stringResource(R.string.officer_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                    )
                    Text(
                        text = stringResource(R.string.onboarding_role_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    RoleChoice(
                        title = stringResource(R.string.onboarding_role_patrol),
                        description = stringResource(R.string.onboarding_role_patrol_desc),
                        selected = role == PatrolRole.PATROL,
                        testTag = "onboarding_role_patrol",
                        onClick = { role = PatrolRole.PATROL },
                    )
                    RoleChoice(
                        title = stringResource(R.string.onboarding_role_supervisor),
                        description = stringResource(R.string.onboarding_role_supervisor_desc),
                        selected = role == PatrolRole.SUPERVISOR,
                        testTag = "onboarding_role_supervisor",
                        onClick = { role = PatrolRole.SUPERVISOR },
                    )
                    PrimaryButton(
                        text = stringResource(R.string.onboarding_continue),
                        onClick = { step = 2 },
                        enabled = officerName.isNotBlank(),
                        modifier = Modifier.testTag("onboarding_identity_continue"),
                    )
                }
                else -> {
                    Text(
                        text = stringResource(R.string.onboarding_privacy_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = stringResource(R.string.onboarding_privacy_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    PrimaryButton(
                        text = stringResource(R.string.onboarding_get_started),
                        onClick = { onComplete(officerName.trim(), role) },
                        modifier = Modifier.testTag("onboarding_get_started"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleChoice(
    title: String,
    description: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.padding(start = 8.dp)) {
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
