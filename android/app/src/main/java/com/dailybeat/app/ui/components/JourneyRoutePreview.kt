package com.dailybeat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dailybeat.app.R
import com.dailybeat.app.data.model.LocationVisit

private val RouteAmber = Color(0xFFB7791F)
private val StopCoral = Color(0xFFE76F51)

internal fun previewFractions(pointCount: Int): List<Float> = when {
    pointCount <= 0 -> emptyList()
    pointCount == 1 -> listOf(0.5f)
    else -> List(pointCount) { index -> index.toFloat() / (pointCount - 1).toFloat() }
}

@Composable
fun JourneyRoutePreview(
    visits: List<LocationVisit>,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val model = remember(visits) { JourneyMapModel.fromVisits(visits) }
    if (model.points.isEmpty()) return

    val orderFractions = remember(model.points.size) { previewFractions(model.points.size) }
    val minLatitude = remember(model.points) { model.points.minOf { it.latitude } }
    val maxLatitude = remember(model.points) { model.points.maxOf { it.latitude } }
    val previewDescription = stringResource(
        R.string.journey_preview_content_description,
        model.points.size,
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_route_preview"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .semantics { contentDescription = previewDescription },
            ) {
                val inset = 16.dp.toPx()
                val routeWidth = (size.width - inset * 2).coerceAtLeast(0f)
                val routeHeight = (size.height - inset * 2).coerceAtLeast(0f)
                val latitudeSpan = maxLatitude - minLatitude
                val offsets = model.points.mapIndexed { index, point ->
                    val yFraction = if (latitudeSpan == 0.0) {
                        0.5f
                    } else {
                        ((maxLatitude - point.latitude) / latitudeSpan).toFloat()
                    }
                    Offset(
                        x = inset + orderFractions[index] * routeWidth,
                        y = inset + yFraction * routeHeight,
                    )
                }

                offsets.zipWithNext().forEach { (start, end) ->
                    drawLine(
                        color = RouteAmber,
                        start = start,
                        end = end,
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                offsets.forEach { offset ->
                    drawCircle(
                        color = StopCoral,
                        radius = 6.dp.toPx(),
                        center = offset,
                    )
                }
            }

            Text(
                text = pluralStringResource(
                    R.plurals.journey_map_points,
                    model.points.size,
                    model.points.size,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SecondaryButton(
                text = stringResource(R.string.journey_map_open),
                onClick = onOpenMap,
                modifier = Modifier.testTag("open_full_map"),
            )
        }
    }
}
