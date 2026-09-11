package com.dailybeat.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailybeat.app.R
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.components.JourneyMapPreview
import com.dailybeat.app.util.DateKeys
import com.dailybeat.app.util.Formatters

@Composable
fun JourneyMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JourneyMapViewModel = viewModel(),
) {
    val context = LocalContext.current
    val model by viewModel.model.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("journey_map_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("journey_map_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.journey_map_back_content_description),
                )
            }
            Text(
                text = if (viewModel.date == DateKeys.today()) {
                    stringResource(R.string.journey_map_title)
                } else {
                    stringResource(R.string.journey_map_title_for_day, Formatters.dayHeading(viewModel.date))
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        if (model.points.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.journey_empty_title),
                subtitle = stringResource(R.string.journey_empty_subtitle),
            )
        } else {
            JourneyMapPreview(
                model = model,
                modifier = Modifier.weight(1f),
                onFailure = { message ->
                    OperationalFailureLog.record(
                        context = context,
                        category = "map",
                        retryable = true,
                        message = message,
                    )
                },
            )
        }
    }
}
