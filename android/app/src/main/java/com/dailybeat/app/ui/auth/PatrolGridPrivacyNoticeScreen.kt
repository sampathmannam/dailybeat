package com.dailybeat.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import com.dailybeat.app.patrolgrid.PATROLGRID_LOCAL_RETENTION_DAYS
import java.net.URI

const val PATROLGRID_PRIVACY_NOTICE_VERSION = 3

fun isPatrolGridPrivacyPolicyConfigured(policyUrl: String, retentionDays: Int): Boolean =
    retentionDays == PATROLGRID_LOCAL_RETENTION_DAYS && runCatching {
        URI(policyUrl.trim()).let { uri ->
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }
    }.getOrDefault(false)

@Composable
fun PatrolGridPrivacyGate(
    acknowledgedNoticeVersion: Int,
    policyUrl: String,
    retentionDays: Int,
    onAcknowledge: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    if (acknowledgedNoticeVersion >= PATROLGRID_PRIVACY_NOTICE_VERSION) {
        content()
    } else {
        PatrolGridPrivacyNoticeScreen(
            policyUrl = policyUrl,
            retentionDays = retentionDays,
            onAcknowledge = { onAcknowledge(PATROLGRID_PRIVACY_NOTICE_VERSION) },
        )
    }
}

@Composable
fun PatrolGridPrivacyNoticeScreen(
    policyUrl: String,
    retentionDays: Int,
    onAcknowledge: () -> Unit,
    onOpenPrivacyPolicy: ((String) -> Unit)? = null,
) {
    val privacyConfigured = isPatrolGridPrivacyPolicyConfigured(policyUrl, retentionDays)
    val uriHandler = LocalUriHandler.current
    var policyOpenFailed by remember { mutableStateOf(false) }
    val sections = listOf(
        NoticeSection(
            testTag = "privacy_tracking_boundaries",
            title = stringResource(R.string.patrolgrid_privacy_tracking_title),
            body = stringResource(R.string.patrolgrid_privacy_tracking_body),
        ),
        NoticeSection(
            testTag = "privacy_supervisor_visibility",
            title = stringResource(R.string.patrolgrid_privacy_visibility_title),
            body = stringResource(R.string.patrolgrid_privacy_visibility_body),
        ),
        NoticeSection(
            testTag = "privacy_human_review",
            title = stringResource(R.string.patrolgrid_privacy_human_review_title),
            body = stringResource(R.string.patrolgrid_privacy_human_review_body),
        ),
        NoticeSection(
            testTag = "privacy_map_provider",
            title = stringResource(R.string.patrolgrid_privacy_map_provider_title),
            body = stringResource(R.string.patrolgrid_privacy_map_provider_body),
        ),
        NoticeSection(
            testTag = "privacy_retention",
            title = stringResource(R.string.patrolgrid_privacy_retention_title),
            body = if (privacyConfigured) {
                stringResource(R.string.patrolgrid_privacy_retention_body, retentionDays)
            } else {
                stringResource(R.string.patrolgrid_privacy_retention_unconfigured)
            },
        ),
        NoticeSection(
            testTag = "privacy_context_support",
            title = stringResource(R.string.patrolgrid_privacy_context_support_title),
            body = stringResource(R.string.patrolgrid_privacy_context_support_body),
        ),
    )

    Surface(modifier = Modifier.fillMaxSize().testTag("privacy_notice")) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("privacy_notice_list"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.patrolgrid_privacy_title),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.patrolgrid_privacy_version,
                        PATROLGRID_PRIVACY_NOTICE_VERSION,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (!privacyConfigured) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag("privacy_policy_unconfigured"),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = stringResource(R.string.patrolgrid_privacy_synthetic_qa_only),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            items(sections, key = NoticeSection::testTag) { section ->
                PrivacyNoticeSection(section)
            }
            item { HorizontalDivider() }
            if (privacyConfigured) {
                item {
                    TextButton(
                        onClick = {
                            policyOpenFailed = runCatching {
                                onOpenPrivacyPolicy?.invoke(policyUrl) ?: uriHandler.openUri(policyUrl)
                            }.isFailure
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .testTag("privacy_policy_link"),
                    ) {
                        Text(stringResource(R.string.patrolgrid_privacy_open_policy))
                    }
                }
                if (policyOpenFailed) {
                    item {
                        Text(
                            text = stringResource(R.string.patrolgrid_privacy_policy_open_error),
                            modifier = Modifier.testTag("privacy_policy_open_error"),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.patrolgrid_privacy_acknowledgement_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("privacy_acknowledge"),
                ) {
                    Text(
                        if (privacyConfigured) {
                            stringResource(R.string.patrolgrid_privacy_acknowledge)
                        } else {
                            stringResource(R.string.patrolgrid_privacy_continue_synthetic_qa)
                        },
                    )
                }
            }
        }
    }
}

private data class NoticeSection(
    val testTag: String,
    val title: String,
    val body: String,
)

@Composable
private fun PrivacyNoticeSection(section: NoticeSection) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().testTag(section.testTag),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = section.title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = section.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
