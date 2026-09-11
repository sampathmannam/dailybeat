package com.dailybeat.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.R
import org.maplibre.android.MapLibre
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROUTE_SOURCE_ID = "dailybeat-route-source"
private const val ROUTE_CASING_LAYER_ID = "dailybeat-route-casing-layer"
private const val ROUTE_LAYER_ID = "dailybeat-route-layer"
private const val GAP_SOURCE_ID = "dailybeat-gap-source"
private const val GAP_LAYER_ID = "dailybeat-gap-layer"
private const val STOP_SOURCE_ID = "dailybeat-stop-source"
private const val STOP_LAYER_ID = "dailybeat-stop-layer"
private const val PLAYBACK_SOURCE_ID = "dailybeat-playback-source"
private const val PLAYBACK_LAYER_ID = "dailybeat-playback-layer"

@Composable
fun JourneyMapPreview(
    model: JourneyMapModel,
    modifier: Modifier = Modifier,
    onFailure: (String) -> Unit = {},
) {
    val context = LocalContext.current
    if (model.points.isEmpty()) return

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var mapError by remember { mutableStateOf(false) }
    var mapRendered by remember { mutableStateOf(false) }
    var mapViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var externalMapError by remember { mutableStateOf(false) }
    val playbackProgress = remember(model.points) { Animatable(1f) }
    var isPlaying by remember(model.points) { mutableStateOf(false) }
    val playbackScope = rememberCoroutineScope()
    val replayablePointCount = model.points.count { it.drawsRoute }
    val canReplay = mapRendered && replayablePointCount >= 2
    val mapDescription = stringResource(R.string.journey_map_content_description)
    val readyMapDescription = stringResource(R.string.journey_map_ready_content_description)
    val loadStyle: (MapLibreMap) -> Unit = { readyMap ->
        mapError = false
        mapRendered = false
        readyMap.setStyle(JOURNEY_MAP_STYLE_URL) { style ->
            loadedStyle = style
            mapError = false
        }
    }
    val mapView = rememberMapViewWithLifecycle(
        onMapReady = { readyMap ->
            map = readyMap
            loadStyle(readyMap)
        },
        onMapError = {
            mapError = true
            onFailure("MapLibre map loading failed.")
        },
    )

    LaunchedEffect(model.points) {
        isPlaying = false
        playbackProgress.snapTo(1f)
    }

    LaunchedEffect(isPlaying, model.points) {
        if (!isPlaying) {
            playbackProgress.stop()
            return@LaunchedEffect
        }
        val remainingDuration = (
            journeyPlaybackDurationMillis(replayablePointCount) *
                (1f - playbackProgress.value)
            ).roundToInt().coerceAtLeast(1)
        playbackProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(remainingDuration, easing = LinearEasing),
        )
        isPlaying = false
    }

    DisposableEffect(map, loadedStyle, model, mapView, mapViewportSize) {
        val readyMap = map
        val style = loadedStyle
        if (readyMap == null || style == null || mapViewportSize == IntSize.Zero) {
            onDispose { }
        } else {
            mapRendered = false
            mapView.contentDescription = mapDescription
            lateinit var renderListener: MapView.OnDidFinishRenderingMapListener
            renderListener = MapView.OnDidFinishRenderingMapListener { fullyRendered ->
                if (fullyRendered) {
                    mapRendered = true
                    mapView.contentDescription = readyMapDescription
                    mapView.removeOnDidFinishRenderingMapListener(renderListener)
                }
            }
            mapView.addOnDidFinishRenderingMapListener(renderListener)
            val density = mapView.resources.displayMetrics.density
            val cameraPaddingPx = (48 * density).roundToInt()
            readyMap.renderJourney(
                style = style,
                model = model,
                viewportWidthPx = mapViewportSize.width,
                viewportHeightPx = mapViewportSize.height,
                cameraPaddingPx = cameraPaddingPx,
                onError = {
                    mapView.removeOnDidFinishRenderingMapListener(renderListener)
                    mapRendered = false
                    mapView.contentDescription = mapDescription
                    mapError = true
                    onFailure("MapLibre journey render failed.")
                },
            )
            onDispose {
                mapView.removeOnDidFinishRenderingMapListener(renderListener)
            }
        }
    }

    LaunchedEffect(map, loadedStyle, model) {
        val readyMap = map ?: return@LaunchedEffect
        val style = loadedStyle ?: return@LaunchedEffect
        var lastRenderedProgress = Float.NaN
        var lastRenderedPlaying = false
        snapshotFlow { playbackProgress.value to isPlaying }.collect { (progress, playing) ->
            val shouldRender = lastRenderedProgress.isNaN() ||
                playing != lastRenderedPlaying ||
                !playing ||
                progress >= 0.999f ||
                abs(progress - lastRenderedProgress) >= 0.015f
            if (shouldRender) {
                readyMap.renderPlaybackFrame(
                    style = style,
                    model = model.atPlaybackProgress(progress),
                    showPosition = playing || progress < 0.999f,
                    onError = {
                        mapError = true
                        onFailure("MapLibre route replay failed.")
                    },
                )
                lastRenderedProgress = progress
                lastRenderedPlaying = playing
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_map_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (!mapError) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { mapViewportSize = it }
                            .testTag("journey_map"),
                    )
                }
                if (!mapRendered || mapError) {
                    JourneyMapSnapshot(
                        model = model,
                        modifier = Modifier.fillMaxSize(),
                        testTag = "journey_map_fallback",
                        readyTestTag = "journey_map_fallback_ready",
                        onFailure = onFailure,
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    ) {
                        if (mapError) {
                            Row(
                                modifier = Modifier.padding(start = 14.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.journey_map_unavailable),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(
                                    onClick = {
                                        loadedStyle = null
                                        mapView.contentDescription = mapDescription
                                        map?.let(loadStyle)
                                    },
                                ) {
                                    Text(stringResource(R.string.journey_map_retry))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(
                                    text = stringResource(R.string.journey_map_loading_interactive),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (mapRendered) {
                    Spacer(
                        modifier = Modifier
                            .size(1.dp)
                            .testTag("journey_map_ready"),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { playbackProgress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("route_replay_progress"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            when {
                                isPlaying -> isPlaying = false
                                playbackProgress.value >= 0.999f -> playbackScope.launch {
                                    playbackProgress.snapTo(0f)
                                    isPlaying = true
                                }
                                else -> isPlaying = true
                            }
                        },
                        enabled = canReplay,
                        modifier = Modifier.testTag("replay_route"),
                    ) {
                        Icon(
                            imageVector = when {
                                isPlaying -> Icons.Filled.Pause
                                playbackProgress.value >= 0.999f -> Icons.Filled.Replay
                                else -> Icons.Filled.PlayArrow
                            },
                            contentDescription = null,
                        )
                        Text(
                            text = when {
                                isPlaying -> stringResource(R.string.journey_map_pause_replay)
                                playbackProgress.value >= 0.999f -> stringResource(R.string.journey_map_replay)
                                else -> stringResource(R.string.journey_map_resume_replay)
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.journey_map_replay_progress,
                            (playbackProgress.value * 100).roundToInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
                    onClick = {
                        model.openStreetMapUrlOrNull?.let { url ->
                            externalMapError = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }.isFailure
                        }
                    },
                ) {
                    Text(stringResource(R.string.journey_map_open_external))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (externalMapError) {
                Text(
                    text = stringResource(R.string.journey_map_open_error),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun MapLibreMap.renderPlaybackFrame(
    style: Style,
    model: JourneyMapModel,
    showPosition: Boolean,
    onError: () -> Unit,
) {
    runCatching {
        val routeFeatures = model.routeSegments
            .filter { it.size >= 2 }
            .map { segment ->
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        segment.map { Point.fromLngLat(it.longitude, it.latitude) },
                    ),
                )
            }
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)?.setGeoJson(
            FeatureCollection.fromFeatures(routeFeatures),
        )

        val gapFeatures = model.gapSegments.map { segment ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    segment.map { Point.fromLngLat(it.longitude, it.latitude) },
                ),
            )
        }
        style.getSourceAs<GeoJsonSource>(GAP_SOURCE_ID)?.setGeoJson(
            FeatureCollection.fromFeatures(gapFeatures),
        )

        val current = model.points.lastOrNull { it.drawsRoute }
        if (showPosition && current != null) {
            val position = FeatureCollection.fromFeatures(
                listOf(Feature.fromGeometry(Point.fromLngLat(current.longitude, current.latitude))),
            )
            val source = style.getSourceAs<GeoJsonSource>(PLAYBACK_SOURCE_ID)
            if (source == null) {
                style.addSource(GeoJsonSource(PLAYBACK_SOURCE_ID, position))
                style.addLayer(
                    CircleLayer(PLAYBACK_LAYER_ID, PLAYBACK_SOURCE_ID).withProperties(
                        circleColor(JOURNEY_ROUTE_CASING_COLOR),
                        circleRadius(7f),
                        circleStrokeColor(JOURNEY_ROUTE_COLOR),
                        circleStrokeWidth(3f),
                    ),
                )
            } else {
                source.setGeoJson(position)
            }
        } else if (style.getSource(PLAYBACK_SOURCE_ID) != null) {
            style.removeLayer(PLAYBACK_LAYER_ID)
            style.removeSource(PLAYBACK_SOURCE_ID)
        }
    }.onFailure { onError() }
}

@Composable
private fun rememberMapViewWithLifecycle(
    onMapReady: (MapLibreMap) -> Unit,
    onMapError: () -> Unit,
): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            contentDescription = context.getString(R.string.journey_map_content_description)
            onCreate(Bundle())
            addOnDidFailLoadingMapListener { onMapError() }
            getMapAsync(onMapReady)
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

private fun MapLibreMap.renderJourney(
    style: Style,
    model: JourneyMapModel,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    cameraPaddingPx: Int,
    onError: () -> Unit,
) {
    runCatching {
        uiSettings.isAttributionEnabled = true
        uiSettings.isLogoEnabled = true

        val points = model.stopPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
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
            val route = FeatureCollection.fromFeatures(routeFeatures)
            val routeSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            if (routeSource == null) {
                style.addSource(
                    GeoJsonSource(
                        ROUTE_SOURCE_ID,
                        route,
                    ),
                )
                style.addLayer(
                    LineLayer(ROUTE_CASING_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        lineColor(JOURNEY_ROUTE_CASING_COLOR),
                        lineWidth(8f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style.addLayer(
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        lineColor(JOURNEY_ROUTE_COLOR),
                        lineWidth(5f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
            } else {
                routeSource.setGeoJson(route)
            }
        } else if (style.getSource(ROUTE_SOURCE_ID) != null) {
            style.removeLayer(ROUTE_LAYER_ID)
            style.removeLayer(ROUTE_CASING_LAYER_ID)
            style.removeSource(ROUTE_SOURCE_ID)
        }

        val gapFeatures = model.gapSegments.map { segment ->
            Feature.fromGeometry(
                LineString.fromLngLats(
                    segment.map { Point.fromLngLat(it.longitude, it.latitude) },
                ),
            )
        }
        if (gapFeatures.isNotEmpty()) {
            val gaps = FeatureCollection.fromFeatures(gapFeatures)
            val gapSource = style.getSourceAs<GeoJsonSource>(GAP_SOURCE_ID)
            if (gapSource == null) {
                style.addSource(GeoJsonSource(GAP_SOURCE_ID, gaps))
                style.addLayer(
                    LineLayer(GAP_LAYER_ID, GAP_SOURCE_ID).withProperties(
                        lineColor("#64748B"),
                        lineWidth(3f),
                        lineDasharray(arrayOf(1.5f, 1.5f)),
                        lineCap(Property.LINE_CAP_ROUND),
                    ),
                )
            } else {
                gapSource.setGeoJson(gaps)
            }
        } else if (style.getSource(GAP_SOURCE_ID) != null) {
            style.removeLayer(GAP_LAYER_ID)
            style.removeSource(GAP_SOURCE_ID)
        }

        val stops = FeatureCollection.fromFeatures(points.map(Feature::fromGeometry))
        val stopSource = style.getSourceAs<GeoJsonSource>(STOP_SOURCE_ID)
        if (stopSource == null) {
            style.addSource(
                GeoJsonSource(
                    STOP_SOURCE_ID,
                    stops,
                ),
            )
            style.addLayer(
                CircleLayer(STOP_LAYER_ID, STOP_SOURCE_ID).withProperties(
                    circleColor(JOURNEY_STOP_COLOR),
                    circleRadius(6f),
                    circleStrokeColor(JOURNEY_STOP_STROKE_COLOR),
                    circleStrokeWidth(2f),
                ),
            )
        } else {
            stopSource.setGeoJson(stops)
        }

        if (model.points.size == 1 || model.crossesAntimeridian) {
            animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(
                            LatLng(
                                requireNotNull(model.centerLatitude),
                                requireNotNull(model.centerLongitude),
                            ),
                        )
                        .zoom(
                            model.cameraZoomForViewport(
                                widthPx = viewportWidthPx,
                                heightPx = viewportHeightPx,
                                paddingPx = cameraPaddingPx,
                            ).toDouble(),
                        )
                        .build(),
                ),
            )
        } else {
            val bounds = model.bounds
            animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                    cameraPaddingPx,
                ),
            )
        }
    }.onFailure { onError() }
}
