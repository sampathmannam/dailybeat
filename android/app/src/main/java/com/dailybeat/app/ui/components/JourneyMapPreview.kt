package com.dailybeat.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.R
import com.dailybeat.app.ui.theme.Gold
import com.dailybeat.app.ui.theme.Ink
import com.dailybeat.app.util.Formatters
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
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAnchor
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textMaxWidth
import org.maplibre.android.style.layers.PropertyFactory.textOffset
import org.maplibre.android.style.layers.PropertyFactory.textOptional
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import com.dailybeat.app.ui.theme.LocalDarkTheme
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.maps.MapPreferences
import com.dailybeat.app.maps.LocalMapLease
import kotlinx.coroutines.delay
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
private const val STOP_LABEL_LAYER_ID = "dailybeat-stop-label-layer"
private const val STOP_LABEL_PROPERTY = "dailybeat-stop-label"
private const val PLAYBACK_SOURCE_ID = "dailybeat-playback-source"
private const val PLAYBACK_LAYER_ID = "dailybeat-playback-layer"

/** Keeps a replay snapshot while live geometry is preserved; replacement/removal invalidates it. */
internal class JourneyPlaybackState {
    val progress = Animatable(1f)
    var isPlaying by mutableStateOf(false)
        private set
    private var replayModel by mutableStateOf<JourneyMapModel?>(null)

    fun visibleModel(liveModel: JourneyMapModel): JourneyMapModel =
        replayModel?.takeIf { liveModel.preservesReplayOf(it) } ?: liveModel

    suspend fun acceptModel(liveModel: JourneyMapModel) {
        if (replayModel?.let { !liveModel.preservesReplayOf(it) } == true) {
            // Never retain a hidden/erased/replaced point for the sake of a smooth animation.
            pause()
            replayModel = null
            progress.snapTo(1f)
        }
    }

    suspend fun toggle(liveModel: JourneyMapModel) {
        acceptModel(liveModel)
        if (isPlaying) {
            pause()
        } else if (visibleModel(liveModel).canReplay) {
            if (progress.value >= 0.999f) {
                replayModel = liveModel
                progress.snapTo(0f)
            }
            isPlaying = true
        }
    }

    fun pause() { isPlaying = false }

    suspend fun seek(liveModel: JourneyMapModel, value: Float) {
        acceptModel(liveModel)
        if (!value.isFinite() || !visibleModel(liveModel).canReplay) return
        pause()
        if (replayModel == null) replayModel = liveModel
        progress.snapTo(value.coerceIn(0f, 1f))
    }

    suspend fun playToEnd() {
        val replay = replayModel ?: return
        val remainingDuration = (journeyPlaybackDurationMillis(replay.points.count { it.drawsRoute }) *
            (1f - progress.value)).roundToInt().coerceAtLeast(1)
        // Compose's duration scale honors Android Remove animations. The seek control below
        // remains usable without animation, rather than overriding that accessibility choice.
        progress.animateTo(1f, tween(remainingDuration, easing = LinearEasing))
        pause()
        replayModel = null
    }
}

@Composable
internal fun rememberJourneyPlaybackState(model: JourneyMapModel): JourneyPlaybackState {
    val state = remember { JourneyPlaybackState() }
    LaunchedEffect(model) { state.acceptModel(model) }
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) state.playToEnd() else state.progress.stop()
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) state.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

@Composable
fun JourneyMapPreview(
    model: JourneyMapModel,
    modifier: Modifier = Modifier,
    onFailure: (String) -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as DailyBeatApp
    val preferences by app.mapSettings.state.collectAsState()
    val offline by app.offlineMaps.state.collectAsState()
    key(preferences, offline.installed?.version) {
        val lease = remember { app.offlineMaps.acquire() }
        JourneyMapPreviewContent(model, modifier, onFailure, preferences, lease)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun JourneyMapPreviewContent(
    model: JourneyMapModel,
    modifier: Modifier,
    onFailure: (String) -> Unit,
    preferences: MapPreferences,
    lease: LocalMapLease?,
    nativeMapEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val app = context.applicationContext as DailyBeatApp
    val dark = LocalDarkTheme.current
    var insideCoverage by remember { mutableStateOf(lease?.contains(model.centerLatitude ?: 0.0, model.centerLongitude ?: 0.0) == true) }
    val useOffline = lease != null && (!preferences.allowOnlineMaps || insideCoverage)
    var loadAttempt by remember { mutableStateOf(0) }
    val styleGeneration = remember { java.util.concurrent.atomic.AtomicInteger() }
    var fittedModel by remember { mutableStateOf<JourneyMapModel?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var mapError by remember { mutableStateOf(!nativeMapEnabled) }
    var mapRendered by remember { mutableStateOf(false) }
    var mapViewportSize by remember { mutableStateOf(IntSize.Zero) }
    var externalMapError by remember { mutableStateOf(false) }
    val playback = rememberJourneyPlaybackState(model)
    val displayModel = playback.visibleModel(model)
    val mapDescription = stringResource(R.string.journey_map_content_description)
    val readyMapDescription = stringResource(R.string.journey_map_ready_content_description)
    // A deterministic renderer-unavailable seam also exercises the complete local UI in tests.
    val mapView = if (nativeMapEnabled) rememberMapViewWithLifecycle(
        onMapReady = { readyMap -> map = readyMap },
        onMapError = {
            mapError = true
            onFailure("Map could not load. Your route is still available.")
        },
        onDisposeMap = { lease?.close() },
    ) else null
    LaunchedEffect(map, useOffline, dark, loadAttempt) {
        val readyMap = map ?: return@LaunchedEffect
        val generation = styleGeneration.incrementAndGet()
        loadedStyle = null
        mapError = false
        mapRendered = false
        try {
            val builder = when {
                useOffline -> Style.Builder().fromJson(requireNotNull(lease).style(dark))
                preferences.allowOnlineMaps -> Style.Builder().fromUri(preferences.provider.styleUrl)
                else -> Style.Builder().fromJson("{\"version\":8,\"sources\":{},\"layers\":[]}")
            }
            readyMap.setStyle(builder) {
                if (styleGeneration.get() == generation) { loadedStyle = it; mapError = false }
            }
            delay(12_000)
            if (!mapRendered) {
                mapError = true
                app.mapNetwork.resources.dispatcher.cancelAll()
                onFailure("Map took too long to load. Try again.")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { mapError = true; onFailure("Map could not load. Try again.") }
        finally { styleGeneration.compareAndSet(generation, generation + 1) }
    }
    DisposableEffect(map) {
        val readyMap = map
        val listener = MapLibreMap.OnCameraIdleListener {
            readyMap?.cameraPosition?.target?.let {
                insideCoverage = lease?.contains(it.latitude, it.longitude) == true
            }
        }
        readyMap?.addOnCameraIdleListener(listener)
        onDispose { readyMap?.removeOnCameraIdleListener(listener) }
    }

    DisposableEffect(map, loadedStyle, displayModel, mapView, mapViewportSize) {
        val readyMap = map
        val style = loadedStyle
        if (readyMap == null || style == null || mapView == null || mapViewportSize == IntSize.Zero) {
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
                model = displayModel,
                viewportWidthPx = mapViewportSize.width,
                viewportHeightPx = mapViewportSize.height,
                cameraPaddingPx = cameraPaddingPx,
                fitCamera = fittedModel != displayModel,
                onError = {
                    mapView.removeOnDidFinishRenderingMapListener(renderListener)
                    mapRendered = false
                    mapView.contentDescription = mapDescription
                    mapError = true
                    onFailure("MapLibre journey render failed.")
                },
            )
            fittedModel = displayModel
            onDispose {
                mapView.removeOnDidFinishRenderingMapListener(renderListener)
            }
        }
    }

    LaunchedEffect(map, loadedStyle, displayModel, mapError) {
        val readyMap = map ?: return@LaunchedEffect
        val style = loadedStyle ?: return@LaunchedEffect
        if (mapError) return@LaunchedEffect
        var lastRenderedProgress = Float.NaN
        var lastRenderedPlaying = false
        snapshotFlow { playback.progress.value to playback.isPlaying }.collect { (progress, playing) ->
            val shouldRender = lastRenderedProgress.isNaN() ||
                playing != lastRenderedPlaying ||
                !playing ||
                progress >= 0.999f ||
                abs(progress - lastRenderedProgress) >= 0.015f
            if (shouldRender) {
                readyMap.renderPlaybackFrame(
                    style = style,
                    model = displayModel.atPlaybackProgress(progress),
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_map_card"),
    ) {
        val controlsMaxHeight = maxHeight * 0.55f
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (!mapError && mapView != null) {
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
                            model = displayModel,
                            modifier = Modifier.fillMaxSize(),
                            testTag = "journey_map_fallback",
                            readyTestTag = "journey_map_fallback_ready",
                            onFailure = onFailure,
                            allowNetwork = preferences.allowOnlineMaps && !useOffline,
                            playbackProgress = playback.progress.value,
                            showPlaybackPosition = playback.isPlaying || playback.progress.value < 0.999f,
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(12.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ) {
                            if (mapError) {
                                FlowRow(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.journey_map_unavailable),
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(
                                        onClick = {
                                            loadAttempt += 1
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp),
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
                    if (mapRendered && !mapError) {
                        Spacer(
                            modifier = Modifier
                                .size(1.dp)
                                .testTag("journey_map_ready"),
                        )
                    }
                }

                // Large fonts or a short window must not reduce the map to zero height.
                // Keep at least 45% for the route and scroll only the controls when needed.
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(max = controlsMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .testTag("journey_map_controls"),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!preferences.allowOnlineMaps || useOffline) {
                            Text(
                                text = if (useOffline && insideCoverage) stringResource(R.string.map_using_offline)
                                    else if (lease != null) stringResource(R.string.map_outside_coverage)
                                    else stringResource(R.string.map_local_route_only),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        JourneyReplayControls(model = model, playback = playback)
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.journey_map_points,
                                model.points.size,
                                model.points.size,
                            ),
                            modifier = Modifier.align(Alignment.CenterVertically),
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
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.journey_map_open_external),
                                modifier = Modifier.weight(1f, fill = false),
                            )
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
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun JourneyReplayControls(model: JourneyMapModel, playback: JourneyPlaybackState) {
    val scope = rememberCoroutineScope()
    val canReplay = playback.visibleModel(model).canReplay
    val progress = playback.progress.value
    val seekLabel = stringResource(R.string.journey_map_replay)
    Slider(
        value = progress,
        onValueChange = { value -> scope.launch { playback.seek(model, value) } },
        enabled = canReplay,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("route_replay_progress").semantics { contentDescription = seekLabel },
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = { scope.launch { playback.toggle(model) } },
            enabled = canReplay,
            modifier = Modifier.heightIn(min = 48.dp).testTag("replay_route"),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold, contentColor = Ink),
        ) {
            Icon(
                imageVector = when {
                    playback.isPlaying -> Icons.Filled.Pause
                    progress >= 0.999f -> Icons.Filled.Replay
                    else -> Icons.Filled.PlayArrow
                },
                contentDescription = null,
            )
            Text(
                text = when {
                    playback.isPlaying -> stringResource(R.string.journey_map_pause_replay)
                    progress >= 0.999f -> stringResource(R.string.journey_map_replay)
                    else -> stringResource(R.string.journey_map_resume_replay)
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(R.string.journey_map_replay_progress, (progress * 100).roundToInt()),
            modifier = Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    onDisposeMap: () -> Unit,
): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestReady by rememberUpdatedState(onMapReady)
    val latestError by rememberUpdatedState(onMapError)
    val mapView = remember {
        (context.applicationContext as DailyBeatApp).mapNetwork.installNativeClient()
        MapLibre.getInstance(context)
        MapView(context).apply {
            contentDescription = context.getString(R.string.journey_map_content_description)
            onCreate(Bundle())
            addOnDidFailLoadingMapListener { latestError() }
            getMapAsync { latestReady(it) }
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
            onDisposeMap()
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
    fitCamera: Boolean,
    onError: () -> Unit,
) {
    runCatching {
        uiSettings.isAttributionEnabled = true
        uiSettings.isLogoEnabled = true

        val labeledStops = model.labeledStopPoints.toSet()
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

        val stops = FeatureCollection.fromFeatures(
            model.stopPoints.map { point ->
                Feature.fromGeometry(Point.fromLngLat(point.longitude, point.latitude)).apply {
                    if (point in labeledStops) {
                        val label = point.stopLabel.orEmpty() + " · " +
                            Formatters.durationCompact(point.stopDurationMinutes)
                        addStringProperty(STOP_LABEL_PROPERTY, label)
                    }
                }
            },
        )
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
            style.addLayerAbove(
                SymbolLayer(STOP_LABEL_LAYER_ID, STOP_SOURCE_ID).withProperties(
                    textField(Expression.get(STOP_LABEL_PROPERTY)),
                    textSize(12f),
                    textColor("#172630"),
                    textHaloColor("#FFFDF7"),
                    textHaloWidth(1.5f),
                    textMaxWidth(12f),
                    textOffset(arrayOf(0f, 1.2f)),
                    textAnchor(Property.TEXT_ANCHOR_TOP),
                    textOptional(true),
                ),
                STOP_LAYER_ID,
            )
        } else {
            stopSource.setGeoJson(stops)
        }

        if (!fitCamera) return@runCatching
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
