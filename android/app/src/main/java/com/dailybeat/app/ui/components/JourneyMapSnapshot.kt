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
import androidx.compose.runtime.DisposableEffect
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
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.tan

private const val SNAPSHOT_ROUTE_SOURCE_ID = "dailybeat-snapshot-route-source"
private const val SNAPSHOT_ROUTE_CASING_LAYER_ID = "dailybeat-snapshot-route-casing-layer"
private const val SNAPSHOT_ROUTE_LAYER_ID = "dailybeat-snapshot-route-layer"
private const val SNAPSHOT_STOP_SOURCE_ID = "dailybeat-snapshot-stop-source"
private const val SNAPSHOT_STOP_LAYER_ID = "dailybeat-snapshot-stop-layer"

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

    DisposableEffect(context, model, viewportSize) {
        if (viewportSize == IntSize.Zero) {
            onDispose { }
        } else {
            snapshotBitmap = null
            MapLibre.getInstance(context)
            val logicalWidth = (viewportSize.width / density).roundToInt().coerceAtLeast(1)
            val logicalHeight = (viewportSize.height / density).roundToInt().coerceAtLeast(1)
            val cameraPaddingPx = (40 * density).roundToInt()
            val options = MapSnapshotter.Options(logicalWidth, logicalHeight)
                .withPixelRatio(density)
                .withStyleBuilder(snapshotStyle(model))
                .withCameraPosition(
                    CameraPosition.Builder()
                        .target(
                            LatLng(
                                requireNotNull(model.centerLatitude),
                                requireNotNull(model.centerLongitude),
                            ),
                        )
                        .zoom(
                            model.cameraZoomForViewport(
                                widthPx = viewportSize.width,
                                heightPx = viewportSize.height,
                                paddingPx = cameraPaddingPx,
                            ).toDouble(),
                        )
                        .build(),
                )
                .withLogo(true)
            val snapshotter = MapSnapshotter(context, options)
            var disposed = false
            snapshotter.start(
                MapSnapshotter.SnapshotReadyCallback { snapshot ->
                    if (!disposed) snapshotBitmap = snapshot.bitmap
                },
                MapSnapshotter.ErrorHandler { error ->
                    if (!disposed) {
                        snapshotBitmap = null
                        onFailure(error)
                    }
                },
            )
            onDispose {
                disposed = true
                snapshotter.cancel()
            }
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

private fun snapshotStyle(model: JourneyMapModel): Style.Builder {
    val builder = Style.Builder().fromUri(JOURNEY_MAP_STYLE_URL)
    val routeFeatures = model.routeSegments
        .filter { it.size >= 2 }
        .map { segment ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    segment.map { Point.fromLngLat(it.longitude, it.latitude) },
                ),
            )
        }
    if (routeFeatures.isNotEmpty()) {
        builder.withSource(
            GeoJsonSource(
                SNAPSHOT_ROUTE_SOURCE_ID,
                FeatureCollection.fromFeatures(routeFeatures),
            ),
        )
        builder.withLayer(
            LineLayer(SNAPSHOT_ROUTE_CASING_LAYER_ID, SNAPSHOT_ROUTE_SOURCE_ID).withProperties(
                lineColor(JOURNEY_ROUTE_CASING_COLOR),
                lineWidth(8f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        builder.withLayer(
            LineLayer(SNAPSHOT_ROUTE_LAYER_ID, SNAPSHOT_ROUTE_SOURCE_ID).withProperties(
                lineColor(JOURNEY_ROUTE_COLOR),
                lineWidth(5f),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }

    val stops = model.stopPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (stops.isNotEmpty()) {
        builder.withSource(
            GeoJsonSource(
                SNAPSHOT_STOP_SOURCE_ID,
                FeatureCollection.fromFeatures(stops.map(Feature::fromGeometry)),
            ),
        )
        builder.withLayer(
            CircleLayer(SNAPSHOT_STOP_LAYER_ID, SNAPSHOT_STOP_SOURCE_ID).withProperties(
                circleColor(JOURNEY_STOP_COLOR),
                circleRadius(6f),
                circleStrokeColor(JOURNEY_STOP_STROKE_COLOR),
                circleStrokeWidth(2f),
            ),
        )
    }
    return builder
}

@Composable
private fun SnapshotLoadingPreview(model: JourneyMapModel, modifier: Modifier = Modifier) {
    val projected = remember(model.points) { projectPoints(model.points) }
    val stopTimes = remember(model.stopPoints) { model.stopPoints.map { it.startMs }.toSet() }
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
        val offsets = projected.map { (px, py) ->
            Offset(padding + px.toFloat() * usable.width, padding + py.toFloat() * usable.height)
        }
        if (offsets.size > 1) {
            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path = path, color = Navy, style = Stroke(width = 8.dp.toPx()))
            drawPath(path = path, color = Gold, style = Stroke(width = 5.dp.toPx()))
        }
        offsets.forEachIndexed { index, offset ->
            if (model.points[index].startMs !in stopTimes) return@forEachIndexed
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = offset)
            drawCircle(color = Gold, radius = 4.5.dp.toPx(), center = offset)
        }
    }
}

private fun projectPoints(points: List<JourneyPoint>): List<Pair<Double, Double>> {
    if (points.isEmpty()) return emptyList()
    val longitudes = buildList<Double> {
        add(points.first().longitude)
        points.drop(1).forEach { point ->
            var longitude = point.longitude
            while (longitude - last() > 180.0) longitude -= 360.0
            while (longitude - last() < -180.0) longitude += 360.0
            add(longitude)
        }
    }
    val xs = longitudes.map { (it + 180.0) / 360.0 }
    val ys = points.map { point ->
        val latitude = point.latitude.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)
        val radians = latitude * PI / 180.0
        val mercator = ln(tan(radians) + 1.0 / cos(radians))
        if (mercator.isFinite()) (1.0 - mercator / PI) / 2.0 else 0.5
    }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val spanX = maxX - minX
    val spanY = maxY - minY
    val span = maxOf(spanX, spanY)
    if (span <= 0.0 || !span.isFinite()) return points.map { 0.5 to 0.5 }
    val offsetX = (span - spanX) / 2.0
    val offsetY = (span - spanY) / 2.0
    return xs.indices.map { index ->
        ((xs[index] - minX + offsetX) / span) to ((ys[index] - minY + offsetY) / span)
    }
}

private const val MAX_MERCATOR_LATITUDE = 85.05112878
