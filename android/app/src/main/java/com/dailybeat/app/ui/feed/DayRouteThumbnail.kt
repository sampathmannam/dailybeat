package com.dailybeat.app.ui.feed

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dailybeat.app.ui.components.JourneyMapModel
import com.dailybeat.app.ui.components.JourneyMapSnapshot
import com.dailybeat.app.ui.components.JourneyPoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/** A non-interactive OpenStreetMap snapshot keeps Feed scrolling smooth without losing context. */
@Composable
fun DayRouteThumbnail(
    route: List<RoutePoint>,
    modifier: Modifier = Modifier,
    height: Dp = 152.dp,
    contentDescription: String? = null,
) {
    if (route.isEmpty()) return
    val model = remember(route) {
        JourneyMapModel.fromPoints(
            route.mapIndexed { index, point ->
                JourneyPoint(
                    startMs = index.toLong(),
                    latitude = point.latitude,
                    longitude = point.longitude,
                    visitType = if (point.isStay) "dwell" else "transit",
                )
            },
        )
    }
    if (model.points.isEmpty()) return

    JourneyMapSnapshot(
        model = model,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentDescription = contentDescription,
        testTag = "feed_route_map",
        readyTestTag = "feed_route_map_ready",
    )
}

/** Web Mercator projection retained as a pure geometry regression seam. */
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
    if (span <= 0.0 || !span.isFinite()) return route.map { 0.5 to 0.5 }
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

internal fun List<RoutePoint>.spansAnyDistance(): Boolean {
    if (size < 2) return false
    val first = first()
    return any { abs(it.latitude - first.latitude) > 1e-9 || abs(it.longitude - first.longitude) > 1e-9 }
}
