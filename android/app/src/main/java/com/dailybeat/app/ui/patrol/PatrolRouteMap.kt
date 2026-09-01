package com.dailybeat.app.ui.patrol

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.Navy
import com.dailybeat.app.ui.theme.SuccessGreen
import com.dailybeat.app.patrolgrid.PatrolMapPoint

@Composable
fun PatrolRouteMap(
    trackingActive: Boolean,
    visitedPriorityCount: Int,
    recordedPoints: List<PatrolMapPoint> = emptyList(),
    totalRecordedPoints: Int = recordedPoints.size,
    demoMode: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.3f
    val background = if (dark) Color(0xFF0A1E30) else Color(0xFFEFF3F6)
    val street = if (dark) Color(0xFF29445A) else Color(0xFFD3DCE4)
    val boundary = if (dark) Color(0xFF3B9CFF) else Color(0xFF2563B8)
    val remaining = if (dark) Color(0xFF9AAABC) else Color(0xFF64748B)
    val route = if (dark) Color(0xFF4EA5FF) else Navy

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = background,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.9f)
                    .semantics {
                        contentDescription = if (demoMode) {
                            "Demonstration mission map with a suggested route and priority locations"
                        } else if (recordedPoints.isEmpty()) {
                            "Recorded patrol trail has no route points yet"
                        } else {
                            "Recorded patrol trail showing ${recordedPoints.size} of $totalRecordedPoints route points"
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                if (demoMode) {
                    listOf(
                        Pair(0.12f, 0.30f) to Pair(0.88f, 0.18f),
                        Pair(0.08f, 0.72f) to Pair(0.82f, 0.86f),
                        Pair(0.25f, 0.04f) to Pair(0.18f, 0.95f),
                        Pair(0.62f, 0.02f) to Pair(0.72f, 0.96f),
                    ).forEach { (start, end) ->
                        drawLine(
                            color = street,
                            start = Offset(start.first * w, start.second * h),
                            end = Offset(end.first * w, end.second * h),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }

                if (!demoMode && recordedPoints.isEmpty()) {
                    drawCircle(
                        color = remaining,
                        radius = 7.dp.toPx(),
                        center = Offset(w / 2f, h / 2f),
                    )
                    return@Canvas
                }

                if (!demoMode) {
                    val minLatitude = recordedPoints.minOf { it.latitude }
                    val maxLatitude = recordedPoints.maxOf { it.latitude }
                    val minLongitude = recordedPoints.minOf { it.longitude }
                    val maxLongitude = recordedPoints.maxOf { it.longitude }
                    val latitudeSpan = (maxLatitude - minLatitude).takeIf { it > 0.000001 } ?: 0.000001
                    val longitudeSpan = (maxLongitude - minLongitude).takeIf { it > 0.000001 } ?: 0.000001
                    val padding = 0.12f
                    val actual = recordedPoints.map { point ->
                        Offset(
                            x = (padding + ((point.longitude - minLongitude) / longitudeSpan).toFloat() * (1f - 2f * padding)) * w,
                            y = ((1f - padding) - ((point.latitude - minLatitude) / latitudeSpan).toFloat() * (1f - 2f * padding)) * h,
                        )
                    }
                    actual.zipWithNext().forEach { (start, end) ->
                        drawLine(route, start, end, 4.dp.toPx(), cap = StrokeCap.Round)
                    }
                    val start = actual.first()
                    val end = actual.last()
                    drawCircle(SuccessGreen, 7.dp.toPx(), start)
                    drawCircle(Color.White, 7.dp.toPx(), start, style = Stroke(2.dp.toPx()))
                    drawCircle(route, 8.dp.toPx(), end)
                    drawCircle(Color.White, 8.dp.toPx(), end, style = Stroke(2.dp.toPx()))
                    return@Canvas
                }

                val zone = Path().apply {
                    moveTo(0.20f * w, 0.18f * h)
                    lineTo(0.72f * w, 0.10f * h)
                    lineTo(0.88f * w, 0.42f * h)
                    lineTo(0.78f * w, 0.86f * h)
                    lineTo(0.30f * w, 0.90f * h)
                    lineTo(0.12f * w, 0.58f * h)
                    close()
                }
                drawPath(zone, boundary, style = Stroke(width = 2.dp.toPx()))

                val routePoints = listOf(
                    Offset(0.18f * w, 0.68f * h),
                    Offset(0.31f * w, 0.54f * h),
                    Offset(0.43f * w, 0.62f * h),
                    Offset(0.55f * w, 0.40f * h),
                    Offset(0.72f * w, 0.48f * h),
                    Offset(0.80f * w, 0.26f * h),
                )
                for (index in 0 until routePoints.lastIndex) {
                    drawLine(
                        color = if (index < visitedPriorityCount.coerceAtMost(3)) SuccessGreen else remaining,
                        start = routePoints[index],
                        end = routePoints[index + 1],
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = if (index >= visitedPriorityCount.coerceAtMost(3)) {
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        } else null,
                    )
                }

                val priorities = listOf(routePoints[1], routePoints[3], routePoints[5])
                priorities.forEachIndexed { index, point ->
                    val color = if (index < visitedPriorityCount) SuccessGreen else Gold
                    drawCircle(color, 8.dp.toPx(), point)
                    drawCircle(Color.White, 8.dp.toPx(), point, style = Stroke(2.dp.toPx()))
                }
                val currentIndex = visitedPriorityCount.coerceAtMost(routePoints.lastIndex)
                val current = routePoints[currentIndex]
                drawCircle(route.copy(alpha = if (trackingActive) 0.24f else 0.12f), 18.dp.toPx(), current)
                drawCircle(route, 7.dp.toPx(), current)
                drawCircle(Color.White, 7.dp.toPx(), current, style = Stroke(2.dp.toPx()))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (demoMode) {
                    MapLegendItem(color = route, label = "Suggested route")
                    MapLegendItem(color = SuccessGreen, label = "Completed")
                    MapLegendItem(color = Gold, label = "Priority")
                } else if (recordedPoints.isEmpty()) {
                    Text(
                        "No route points recorded yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MapLegendItem(color = SuccessGreen, label = "Start")
                    MapLegendItem(color = route, label = "Latest point")
                    Text(
                        if (totalRecordedPoints > recordedPoints.size) {
                            "Latest ${recordedPoints.size} of $totalRecordedPoints"
                        } else {
                            "${recordedPoints.size} recorded"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapLegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Color.luminance(): Float =
    (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
