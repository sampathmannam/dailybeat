package com.dailybeat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import com.dailybeat.app.data.model.LocationVisit

/** A stable Strava-style map card; the dedicated map screen remains interactive. */
@Composable
fun JourneyRoutePreview(
    visits: List<LocationVisit>,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(visits) { JourneyMapModel.fromVisits(visits) }
    if (model.points.isEmpty()) return
    val previewDescription = pluralStringResource(
        R.plurals.journey_preview_content_description,
        model.points.size,
        model.points.size,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_route_preview"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column {
            JourneyMapSnapshot(
                model = model,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp),
                contentDescription = previewDescription,
                testTag = "today_journey_map",
                readyTestTag = "today_journey_map_ready",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.journey_map_points,
                        model.points.size,
                        model.points.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onOpenMap,
                    modifier = Modifier.testTag("open_full_map"),
                ) {
                    Text(stringResource(R.string.journey_map_open))
                }
            }
        }
    }
}
