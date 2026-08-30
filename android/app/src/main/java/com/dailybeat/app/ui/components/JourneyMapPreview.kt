package com.dailybeat.app.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.R
import com.dailybeat.app.data.model.LocationVisit
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
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SOURCE_ID = "dailybeat-route-source"
private const val ROUTE_LAYER_ID = "dailybeat-route-layer"
private const val STOP_SOURCE_ID = "dailybeat-stop-source"
private const val STOP_LAYER_ID = "dailybeat-stop-layer"

@Composable
fun JourneyMapPreview(
    visits: List<LocationVisit>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val model = remember(visits) { JourneyMapModel.fromVisits(visits) }
    if (model.points.isEmpty()) return

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember { mutableStateOf<Style?>(null) }
    var mapError by remember { mutableStateOf(false) }
    var externalMapError by remember { mutableStateOf(false) }
    val loadStyle: (MapLibreMap) -> Unit = { readyMap ->
        mapError = false
        readyMap.setStyle(MAP_STYLE_URL) { style ->
            loadedStyle = style
            mapError = false
        }
    }
    val mapView = rememberMapViewWithLifecycle(
        onMapReady = { readyMap ->
            map = readyMap
            loadStyle(readyMap)
        },
        onMapError = { mapError = true },
    )

    LaunchedEffect(map, loadedStyle, model) {
        val readyMap = map
        val style = loadedStyle
        if (readyMap != null && style != null) {
            readyMap.renderJourney(style, model) { mapError = true }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("journey_map_card"),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column {
            if (mapError) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.journey_map_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            loadedStyle = null
                            map?.let(loadStyle)
                        },
                    ) {
                        Text(stringResource(R.string.journey_map_retry))
                    }
                }
            } else {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .testTag("journey_map"),
                )
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
                    Text(stringResource(R.string.journey_map_open))
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
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            if (!destroyed) mapView.onDestroy()
        }
    }
    return mapView
}

private fun MapLibreMap.renderJourney(
    style: Style,
    model: JourneyMapModel,
    onError: () -> Unit,
) {
    runCatching {
        uiSettings.isAttributionEnabled = true
        uiSettings.isLogoEnabled = true

        val points = model.points.map { Point.fromLngLat(it.longitude, it.latitude) }
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
                    LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                        lineColor("#B7791F"),
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
            style.removeSource(ROUTE_SOURCE_ID)
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
                    circleColor("#059669"),
                    circleRadius(6f),
                    circleStrokeColor("#FFFFFF"),
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
                        .zoom(model.cameraZoom.toDouble())
                        .build(),
                ),
            )
        } else {
            val bounds = model.bounds
            animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.from(bounds.north, bounds.east, bounds.south, bounds.west),
                    48,
                ),
            )
        }
    }.onFailure { onError() }
}
