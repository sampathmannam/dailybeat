package com.dailybeat.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.Navy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Renders a non-interactive OpenStreetMap snapshot suitable for scrollable cards. The immediate
 * route fallback prevents an empty panel while tiles load and remains usable when offline.
 */
@Composable
fun JourneyMapSnapshot(
    model: JourneyMapModel,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    testTag: String = "journey_map_snapshot",
    readyTestTag: String = "journey_map_snapshot_ready",
    onFailure: (String) -> Unit = {},
) {
    if (model.points.isEmpty()) return
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var snapshotBitmap by remember(model) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(context, model, viewportSize) {
        if (viewportSize == IntSize.Zero) return@LaunchedEffect
        snapshotBitmap = null
        try {
            snapshotBitmap = withContext(Dispatchers.IO) {
                renderJourneyMapRaster(
                    context = context.applicationContext,
                    model = model,
                    viewportSize = viewportSize,
                    density = density,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            snapshotBitmap = null
            onFailure(error.message ?: "Map tiles are unavailable")
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(testTag)
            .onSizeChanged { viewportSize = it }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        SnapshotLoadingPreview(model = model, modifier = Modifier.fillMaxSize())
        snapshotBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(1.dp)
                    .testTag(readyTestTag),
            )
        }
    }
}

@Composable
private fun SnapshotLoadingPreview(model: JourneyMapModel, modifier: Modifier = Modifier) {
    val projection = remember(model) { JourneyProjection(model) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        val gridStep = size.height / 4f
        var y = gridStep
        while (y < size.height) {
            drawLine(
                color = gridColor.copy(alpha = 0.3f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += gridStep
        }
        var x = gridStep
        while (x < size.width) {
            drawLine(
                color = gridColor.copy(alpha = 0.18f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f,
            )
            x += gridStep
        }

        val padding = 18.dp.toPx()
        val usable = Size(
            width = (size.width - padding * 2).coerceAtLeast(1f),
            height = (size.height - padding * 2).coerceAtLeast(1f),
        )
        fun JourneyPoint.toOffset(): Offset {
            val (px, py) = projection.project(this)
            return Offset(padding + px.toFloat() * usable.width, padding + py.toFloat() * usable.height)
        }

        model.routeSegments.filter { it.size >= 2 }.forEach { segment ->
            val offsets = segment.map { it.toOffset() }
            val routePath = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path = routePath, color = Navy, style = Stroke(width = 8.dp.toPx()))
            drawPath(path = routePath, color = Gold, style = Stroke(width = 5.dp.toPx()))
        }

        model.gapSegments.forEach { segment ->
            val offsets = segment.map { it.toOffset() }
            val gapPath = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(
                path = gapPath,
                color = Color(0xFF64748B),
                style = Stroke(
                    width = 4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(7.dp.toPx(), 6.dp.toPx()),
                    ),
                ),
            )
        }

        model.stopPoints.forEach { point ->
            val offset = point.toOffset()
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = offset)
            drawCircle(color = Gold, radius = 4.5.dp.toPx(), center = offset)
        }
    }
}

private class JourneyProjection(model: JourneyMapModel) {
    private val centerLongitude = requireNotNull(model.centerLongitude)
    private val projected = model.points.map { point -> rawX(point.longitude) to rawY(point.latitude) }
    private val minX = projected.minOf { it.first }
    private val maxX = projected.maxOf { it.first }
    private val minY = projected.minOf { it.second }
    private val maxY = projected.maxOf { it.second }
    private val spanX = maxX - minX
    private val spanY = maxY - minY
    private val span = maxOf(spanX, spanY)
    private val offsetX = (span - spanX) / 2.0
    private val offsetY = (span - spanY) / 2.0

    fun project(point: JourneyPoint): Pair<Double, Double> {
        if (span <= 0.0 || !span.isFinite()) return 0.5 to 0.5
        return ((rawX(point.longitude) - minX + offsetX) / span) to
            ((rawY(point.latitude) - minY + offsetY) / span)
    }

    private fun rawX(longitude: Double): Double {
        var aligned = longitude
        while (aligned - centerLongitude > 180.0) aligned -= 360.0
        while (aligned - centerLongitude < -180.0) aligned += 360.0
        return (aligned + 180.0) / 360.0
    }

    private fun rawY(latitude: Double): Double {
        val safeLatitude = latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val radians = safeLatitude * PI / 180.0
        val mercator = ln(tan(radians) + 1.0 / cos(radians))
        return if (mercator.isFinite()) (1.0 - mercator / PI) / 2.0 else 0.5
    }
}

private const val MAX_MERCATOR_LATITUDE = 85.05112878
