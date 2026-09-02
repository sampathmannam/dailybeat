package com.dailybeat.app.ui.patrol

import android.os.Bundle
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.data.model.PriorityLocation
import com.dailybeat.app.data.model.PriorityLocationState
import com.dailybeat.app.patrolgrid.PatrolMapPoint
import com.dailybeat.app.security.MapCachePrivacy
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.Navy
import com.dailybeat.app.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val PLANNED_SOURCE = "patrolgrid-planned-source"
private const val PLANNED_LAYER = "patrolgrid-planned-layer"
private const val RECORDED_SOURCE = "patrolgrid-recorded-source"
private const val RECORDED_LAYER = "patrolgrid-recorded-layer"
private const val START_SOURCE = "patrolgrid-start-source"
private const val START_LAYER = "patrolgrid-start-layer"
private const val LATEST_SOURCE = "patrolgrid-latest-source"
private const val LATEST_LAYER = "patrolgrid-latest-layer"
private const val MAP_LOAD_TIMEOUT_MS = 20_000L

@Composable
internal fun PatrolGeographicRouteMap(
    trackingActive: Boolean,
    visitedPriorityCount: Int,
    recordedPoints: List<PatrolMapPoint>,
    plannedPoints: List<PatrolMapPoint>,
    priorityLocations: List<PriorityLocation>,
    totalRecordedPoints: Int,
    modifier: Modifier = Modifier,
) {
    val styleUrl = BuildConfig.PATROLGRID_MAP_STYLE_URL
    var mapAttempt by remember(styleUrl) { mutableIntStateOf(0) }
    var mapError by remember(styleUrl, mapAttempt) { mutableStateOf(styleUrl.isBlank()) }
    var map by remember(styleUrl, mapAttempt) { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember(styleUrl, mapAttempt) { mutableStateOf<Style?>(null) }
    var mapRendered by remember(styleUrl, mapAttempt) { mutableStateOf(false) }
    var routeOverlayError by remember(styleUrl, mapAttempt) { mutableStateOf(false) }
    var cameraFitted by remember(styleUrl, mapAttempt) { mutableStateOf(false) }
    var mapViewportSize by remember(styleUrl, mapAttempt) { mutableStateOf(IntSize.Zero) }

    if (mapError) {
        PatrolMapFallback(
            trackingActive = trackingActive,
            visitedPriorityCount = visitedPriorityCount,
            recordedPoints = recordedPoints,
            plannedPoints = plannedPoints,
            priorityLocations = priorityLocations,
            totalRecordedPoints = totalRecordedPoints,
            onRetry = styleUrl.takeIf(String::isNotBlank)?.let {
                {
                    mapAttempt += 1
                }
            },
            modifier = modifier,
        )
        return
    }

    val loadStyle: (MapLibreMap) -> Unit = { readyMap ->
        runCatching {
            readyMap.setStyle(styleUrl) { style -> loadedStyle = style }
        }.onFailure { mapError = true }
    }
    val mapView = rememberPatrolMapView(
        mapAttempt = mapAttempt,
        onMapReady = { readyMap ->
            map = readyMap
            loadStyle(readyMap)
        },
        onMapRendered = {
            if (loadedStyle != null) mapRendered = true
        },
        onMapError = {
            if (!mapRendered) mapError = true
        },
    )

    LaunchedEffect(mapAttempt, mapRendered) {
        if (!mapRendered && patrolMapAttemptExpired(MAP_LOAD_TIMEOUT_MS) { mapRendered }) {
            mapError = true
        }
    }

    DisposableEffect(
        map,
        loadedStyle,
        mapRendered,
        mapViewportSize,
        plannedPoints,
        recordedPoints,
        priorityLocations,
    ) {
        val readyMap = map
        val style = loadedStyle
        if (readyMap == null || style == null) {
            onDispose { }
        } else {
            routeOverlayError = !readyMap.renderPatrolEvidence(
                style = style,
                plannedPoints = plannedPoints,
                recordedPoints = recordedPoints,
                priorityLocations = priorityLocations,
            )
            if (!cameraFitted && mapViewportSize.width > 0 && mapViewportSize.height > 0) {
                cameraFitted = readyMap.fitInitialPatrolEvidence(
                    plannedPoints = plannedPoints,
                    recordedPoints = recordedPoints,
                    priorityLocations = priorityLocations,
                )
            }
            onDispose { }
        }
    }

    val description = when {
        trackingActive && recordedPoints.isEmpty() ->
            "Geographic patrol map is ready and acquiring the first GPS fix"
        recordedPoints.isEmpty() ->
            "Geographic map showing the assigned patrol route and priority locations; no recorded trail yet"
        totalRecordedPoints > recordedPoints.size ->
            "Geographic map showing the assigned route, priority locations, and the latest ${recordedPoints.size} of $totalRecordedPoints recorded points"
        else ->
            "Geographic map showing the assigned route, priority locations, and ${recordedPoints.size} recorded patrol points"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Box {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.9f)
                        .onSizeChanged { mapViewportSize = it }
                        .semantics { contentDescription = description }
                        .testTag("patrol_geographic_map"),
                )
                if (!mapRendered) {
                    Surface(
                        modifier = Modifier
                            .matchParentSize()
                            .testTag("patrol_map_loading"),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Text(
                                text = "Loading secure route map…",
                                modifier = Modifier.padding(top = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            if (routeOverlayError) {
                Text(
                    text = "The basemap is available, but route evidence could not be drawn. The stored evidence was not removed.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("patrol_map_overlay_error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (plannedPoints.isNotEmpty()) GeographicLegendItem(Gold, "Assigned route")
                if (recordedPoints.isNotEmpty()) GeographicLegendItem(Navy, "Recorded trail")
                if (priorityLocations.any { it.latitude != null && it.longitude != null }) {
                    GeographicLegendItem(Gold, "Priority")
                }
                Text(
                    text = when {
                        trackingActive && recordedPoints.isEmpty() -> "Acquiring GPS fix"
                        recordedPoints.isEmpty() -> "Waiting to start"
                        totalRecordedPoints > recordedPoints.size ->
                            "Latest ${recordedPoints.size} of $totalRecordedPoints"
                        else -> "${recordedPoints.size} recorded"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PatrolMapFallback(
    trackingActive: Boolean,
    visitedPriorityCount: Int,
    recordedPoints: List<PatrolMapPoint>,
    plannedPoints: List<PatrolMapPoint>,
    priorityLocations: List<PriorityLocation>,
    totalRecordedPoints: Int,
    onRetry: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("patrol_map_fallback"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline,
            ),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Map tiles are unavailable. Recorded route evidence remains visible offline.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }
        PatrolRouteMap(
            trackingActive = trackingActive,
            visitedPriorityCount = visitedPriorityCount,
            recordedPoints = recordedPoints,
            plannedPoints = plannedPoints,
            priorityLocations = priorityLocations,
            totalRecordedPoints = totalRecordedPoints,
            demoMode = false,
            useGeographicContext = false,
        )
    }
}

@Composable
private fun GeographicLegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
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

@Composable
private fun rememberPatrolMapView(
    mapAttempt: Int,
    onMapReady: (MapLibreMap) -> Unit,
    onMapRendered: () -> Unit,
    onMapError: () -> Unit,
): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnMapReady = rememberUpdatedState(onMapReady)
    val currentOnMapRendered = rememberUpdatedState(onMapRendered)
    val currentOnMapError = rememberUpdatedState(onMapError)
    val mapView = remember(context, lifecycle, mapAttempt) {
        MapCachePrivacy.configure(context)
        MapView(context).apply {
            onCreate(Bundle())
            addOnDidFailLoadingMapListener { currentOnMapError.value() }
            addOnDidFinishRenderingMapListener { fullyRendered ->
                if (fullyRendered) currentOnMapRendered.value()
            }
            getMapAsync { readyMap -> currentOnMapReady.value(readyMap) }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        var destroyed = false
        var started = false
        var resumed = false
        fun start() {
            if (!started) {
                mapView.onStart()
                started = true
            }
        }
        fun resume() {
            start()
            if (!resumed) {
                mapView.onResume()
                resumed = true
            }
        }
        fun pause() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }
        fun stop() {
            pause()
            if (started) {
                mapView.onStop()
                started = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                Lifecycle.Event.ON_DESTROY -> {
                    stop()
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resume()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
            if (!destroyed) mapView.onDestroy()
        }
    }
    return mapView
}

private fun MapLibreMap.renderPatrolEvidence(
    style: Style,
    plannedPoints: List<PatrolMapPoint>,
    recordedPoints: List<PatrolMapPoint>,
    priorityLocations: List<PriorityLocation>,
): Boolean = runCatching {
        uiSettings.isAttributionEnabled = true
        uiSettings.isLogoEnabled = true

        style.upsertLine(
            sourceId = PLANNED_SOURCE,
            layerId = PLANNED_LAYER,
            points = plannedPoints,
            color = "#B7791F",
            width = 4f,
            dashed = true,
        )
        style.upsertLine(
            sourceId = RECORDED_SOURCE,
            layerId = RECORDED_LAYER,
            points = recordedPoints,
            color = "#123A5A",
            width = 5f,
            dashed = false,
        )

        val priorities = priorityLocations.mapNotNull { location ->
            val latitude = location.latitude ?: return@mapNotNull null
            val longitude = location.longitude ?: return@mapNotNull null
            location.state to PatrolMapPoint(latitude, longitude)
        }
        PriorityLocationState.entries.forEach { state ->
            val color = when (state) {
                PriorityLocationState.VISITED -> "#16803C"
                PriorityLocationState.CURRENT -> "#2563B8"
                PriorityLocationState.REMAINING -> "#B7791F"
            }
            style.upsertCircles(
                sourceId = "patrolgrid-priority-${state.name.lowercase()}-source",
                layerId = "patrolgrid-priority-${state.name.lowercase()}-layer",
                points = priorities.filter { it.first == state }.map { it.second },
                color = color,
                radius = 7f,
            )
        }

        style.upsertCircles(
            sourceId = START_SOURCE,
            layerId = START_LAYER,
            points = recordedPoints.firstOrNull()?.let(::listOf).orEmpty(),
            color = "#16803C",
            radius = 7f,
        )
        style.upsertCircles(
            sourceId = LATEST_SOURCE,
            layerId = LATEST_LAYER,
            points = recordedPoints.lastOrNull()?.let(::listOf).orEmpty(),
            color = "#2563B8",
            radius = 8f,
        )
    }.isSuccess

private fun MapLibreMap.fitInitialPatrolEvidence(
    plannedPoints: List<PatrolMapPoint>,
    recordedPoints: List<PatrolMapPoint>,
    priorityLocations: List<PriorityLocation>,
): Boolean {
    val priorities = priorityLocations.mapNotNull { location ->
        val latitude = location.latitude ?: return@mapNotNull null
        val longitude = location.longitude ?: return@mapNotNull null
        PatrolMapPoint(latitude, longitude)
    }
    val allPoints = (plannedPoints + recordedPoints + priorities)
        .filter(PatrolMapPoint::isValidGeographicPoint)
        .distinct()
    if (allPoints.isEmpty()) return false

    return runCatching {
        if (allPoints.size == 1) {
            moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(allPoints.first().latitude, allPoints.first().longitude))
                        .zoom(16.0)
                        .build(),
                ),
            )
        } else {
            val north = allPoints.maxOf { it.latitude }
            val east = allPoints.maxOf { it.longitude }
            val south = allPoints.minOf { it.latitude }
            val west = allPoints.minOf { it.longitude }
            moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(north, east, south, west),
                    64,
                ),
            )
        }
    }.isSuccess
}

private fun PatrolMapPoint.isValidGeographicPoint(): Boolean =
    latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0

internal suspend fun patrolMapAttemptExpired(
    timeoutMs: Long,
    isRendered: () -> Boolean,
): Boolean {
    delay(timeoutMs)
    return !isRendered()
}

private fun Style.upsertLine(
    sourceId: String,
    layerId: String,
    points: List<PatrolMapPoint>,
    color: String,
    width: Float,
    dashed: Boolean,
) {
    if (points.size < 2) {
        if (getLayer(layerId) != null) removeLayer(layerId)
        if (getSource(sourceId) != null) removeSource(sourceId)
        return
    }
    val feature = Feature.fromGeometry(
        LineString.fromLngLats(points.map { Point.fromLngLat(it.longitude, it.latitude) }),
    )
    val source = getSourceAs<GeoJsonSource>(sourceId)
    if (source == null) {
        addSource(GeoJsonSource(sourceId, feature))
        val layer = LineLayer(layerId, sourceId)
        if (dashed) {
            layer.withProperties(
                lineColor(color),
                lineWidth(width),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
                lineDasharray(arrayOf(2f, 2f)),
            )
        } else {
            layer.withProperties(
                lineColor(color),
                lineWidth(width),
                lineCap(Property.LINE_CAP_ROUND),
                lineJoin(Property.LINE_JOIN_ROUND),
            )
        }
        addLayer(layer)
    } else {
        source.setGeoJson(feature)
    }
}

private fun Style.upsertCircles(
    sourceId: String,
    layerId: String,
    points: List<PatrolMapPoint>,
    color: String,
    radius: Float,
) {
    if (points.isEmpty()) {
        if (getLayer(layerId) != null) removeLayer(layerId)
        if (getSource(sourceId) != null) removeSource(sourceId)
        return
    }
    val collection = FeatureCollection.fromFeatures(
        points.map { point -> Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)) },
    )
    val source = getSourceAs<GeoJsonSource>(sourceId)
    if (source == null) {
        addSource(GeoJsonSource(sourceId, collection))
        addLayer(
            CircleLayer(layerId, sourceId).withProperties(
                circleColor(color),
                circleRadius(radius),
                circleStrokeColor("#FFFFFF"),
                circleStrokeWidth(2f),
            ),
        )
    } else {
        source.setGeoJson(collection)
    }
}
