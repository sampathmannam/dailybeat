package com.dailybeat.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.components.EmptyState
import com.dailybeat.app.ui.components.JourneyMapView

@Composable
fun JourneyMapScreen(
    visits: List<LocationVisit>,
    onBack: () -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
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
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("journey_map_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.journey_map_back_content_description),
                )
            }
            Text(
                text = stringResource(R.string.journey_map_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        if (visits.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.journey_empty_title),
                subtitle = stringResource(R.string.journey_empty_subtitle),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                JourneyMapView(
                    visits = visits,
                    isActive = isActive,
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
}
