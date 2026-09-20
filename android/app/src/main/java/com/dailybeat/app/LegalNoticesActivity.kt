package com.dailybeat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailybeat.app.data.settings.ThemePreference
import com.dailybeat.app.ui.components.InlineFeedback
import com.dailybeat.app.ui.components.readableContentWidth
import com.dailybeat.app.ui.theme.DailyBeatTheme

class LegalNoticesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DailyBeatApp
        val thirdPartyNotices = readRawText(R.raw.third_party_notices) +
            "\n\n" + readRawText(R.raw.maplibre_notices)
        val gpl = readRawText(R.raw.gpl_3_0)
        val apache = readRawText(R.raw.apache_license_2_0)

        setContent {
            val preference by app.settingsRepository.themePreference.collectAsStateWithLifecycle()
            val dark = when (preference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            DailyBeatTheme(darkTheme = dark) {
                LegalNoticesScreen(
                    thirdPartyNotices = thirdPartyNotices,
                    gpl = gpl,
                    apache = apache,
                    onBack = ::finish,
                    onOpenSource = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                    },
                )
            }
        }
    }

    private fun readRawText(resourceId: Int): String =
        resources.openRawResource(resourceId).bufferedReader().use { it.readText().trim() }

    private companion object {
        const val SOURCE_URL = "https://github.com/sampathmannam/dailybeat"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalNoticesScreen(
    thirdPartyNotices: String,
    gpl: String,
    apache: String,
    onBack: () -> Unit,
    onOpenSource: () -> Unit,
) {
    var sourceError by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.legal_notices_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.review_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .readableContentWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("legal_notices_list"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.legal_intro_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.legal_notices_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.legal_project_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.legal_project_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(
                            onClick = { sourceError = runCatching(onOpenSource).isFailure },
                            modifier = Modifier.align(Alignment.End).testTag("open_source_repository"),
                        ) {
                            Text(stringResource(R.string.legal_open_source))
                        }
                        if (sourceError) {
                            InlineFeedback(
                                message = stringResource(R.string.legal_open_source_error),
                                isError = true,
                            )
                        }
                    }
                }
            }

            item {
                LegalDocumentSection(
                    title = stringResource(R.string.legal_third_party_title),
                    summary = stringResource(R.string.legal_third_party_summary),
                    document = thirdPartyNotices,
                    testTag = "third_party_notices",
                )
            }
            item {
                LegalDocumentSection(
                    title = stringResource(R.string.legal_gpl_title),
                    summary = stringResource(R.string.legal_gpl_summary),
                    document = gpl,
                    testTag = "gpl_license",
                )
            }
            item {
                LegalDocumentSection(
                    title = stringResource(R.string.legal_apache_title),
                    summary = stringResource(R.string.legal_apache_summary),
                    document = apache,
                    testTag = "apache_license",
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentSection(
    title: String,
    summary: String,
    document: String,
    testTag: String,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.testTag("${testTag}_toggle"),
                ) {
                    Text(
                        stringResource(
                            if (expanded) R.string.legal_hide_full_text
                            else R.string.legal_read_full_text,
                        ),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SelectionContainer {
                    Text(
                        text = document,
                        modifier = Modifier.testTag("${testTag}_content"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
