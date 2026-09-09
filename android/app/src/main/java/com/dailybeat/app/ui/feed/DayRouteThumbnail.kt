package com.dailybeat.app.ui.feed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Draws the shape of a day's travel: the route as a line, each stay as a dot.
 *
 * This is vector drawing rather than a map view on purpose. The feed shows many days at once,
 * and a GL map per card would cost a surface, tile downloads, and a lifecycle each.
 */
@Composable
fun DayRouteThumbnail(
    route: List<RoutePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    contentDescription: String? = null,
) {
    if (route.isEmpty()) return
    val projected = remember(route) { projectToUnitSquare(route) }
    val routeColor = MaterialTheme.colorScheme.primary
    val stayColor = MaterialTheme.colorScheme.primary
    val stayCore = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        drawGrid(gridColor)

        val padding = 18f
        val usable = Size(
            width = (size.width - padding * 2).coerceAtLeast(1f),
            height = (size.height - padding * 2).coerceAtLeast(1f),
        )
        val points = projected.map { (x, y) ->
            Offset(padding + x.toFloat() * usable.width, padding + y.toFloat() * usable.height)
        }

        if (points.size > 1) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = path,
                color = routeColor.copy(alpha = 0.85f),
                style = Stroke(width = 6f),
            )
        }

        points.forEachIndexed { index, offset ->
            if (!route[index].isStay) return@forEachIndexed
            drawCircle(color = stayColor, radius = 11f, center = offset)
            drawCircle(color = stayCore, radius = 5f, center = offset)
        }
    }
}

private fun DrawScope.drawGrid(color: Color) {
    val step = size.height / 4f
    var y = step
    while (y < size.height) {
        drawLine(
            color = color.copy(alpha = 0.35f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += step
    }
}

/**
 * Web Mercator, then scaled into a unit square while preserving aspect ratio so a route is
 * not stretched into a shape the officer never walked. Y is already screen-down.
 */
internal fun projectToUnitSquare(route: List<RoutePoint>): List<Pair<Double, Double>> {
    if (route.isEmpty()) return emptyList()
    val unwrappedLongitudes = buildList<Double> {
        add(route.first().longitude)
        route.drop(1).forEach { point ->
            var longitude = point.longitude
            while (longitude - last() > 180.0) longitude -= 360.0
            while (longitude - last() < -180.0) longitude += 360.0
            add(longitude)
        }
    }
    val xs = unwrappedLongitudes.map(::mercatorX)
    val ys = route.map { mercatorY(it.latitude) }

    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val spanX = maxX - minX
    val spanY = maxY - minY
    val span = maxOf(spanX, spanY)

    if (span <= 0.0 || !span.isFinite()) {
        return route.map { 0.5 to 0.5 }
    }

    // Centre the smaller axis so a mostly north-south day is not pinned to one edge.
    val offsetX = (span - spanX) / 2.0
    val offsetY = (span - spanY) / 2.0
    return xs.indices.map { index ->
        ((xs[index] - minX + offsetX) / span) to ((ys[index] - minY + offsetY) / span)
    }
}

private fun mercatorX(longitude: Double): Double = (longitude + 180.0) / 360.0

private fun mercatorY(latitude: Double): Double {
    val clamped = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
    val radians = clamped * PI / 180.0
    val projected = ln(tan(radians) + 1.0 / cos(radians))
    return if (projected.isFinite()) (1.0 - projected / PI) / 2.0 else 0.5
}

private const val MAX_MERCATOR_LATITUDE = 85.05112878

/** Kept for readability at call sites that only care whether a route is drawable. */
internal fun List<RoutePoint>.spansAnyDistance(): Boolean {
    if (size < 2) return false
    val first = first()
    return any { abs(it.latitude - first.latitude) > 1e-9 || abs(it.longitude - first.longitude) > 1e-9 }
}
