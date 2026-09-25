package com.dailybeat.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.RectF
import androidx.compose.ui.unit.IntSize
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.maps.awaitBoundedBytes
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request

private const val TILE_SIZE_LOGICAL_PX = 256.0
private const val MAP_PADDING_LOGICAL_PX = 40
private const val MAX_TILE_RESPONSE_BYTES = 2L * 1024L * 1024L
private const val MAX_TILE_DIMENSION_PX = 1_024
private const val MAX_TILE_PIXELS = 1_048_576L

internal data class JourneyMapRaster(val bitmap: Bitmap, val complete: Boolean)

/**
 * Builds card-sized maps without MapLibre's native snapshotter. The interactive full map still
 * uses MapLibre, but keeping these short-lived card renders in Android's bitmap stack avoids a
 * native snapshot/MapView teardown race seen on Android 14 x86_64.
 */
internal suspend fun renderJourneyMapRaster(
    context: Context,
    model: JourneyMapModel,
    viewportSize: IntSize,
    density: Float,
    tileBudgetMillis: Long = 8_000,
    loadTile: suspend (Int, Int, Int) -> Bitmap? = { zoom, x, y ->
        OsmRasterTileClient.load(context, zoom, x, y)
    },
): JourneyMapRaster {
    require(viewportSize.width > 0 && viewportSize.height > 0)
    require(viewportSize.width.toLong() * viewportSize.height <= 8_000_000)
    val safeDensity = density.coerceAtLeast(1f)
    val logicalWidth = (viewportSize.width / safeDensity).roundToInt().coerceAtLeast(1)
    val logicalHeight = (viewportSize.height / safeDensity).roundToInt().coerceAtLeast(1)
    val zoom = model.cameraZoomForViewport(
        widthPx = logicalWidth,
        heightPx = logicalHeight,
        paddingPx = MAP_PADDING_LOGICAL_PX,
    )
    val tileSizePx = TILE_SIZE_LOGICAL_PX * safeDensity
    val worldTiles = 2.0.pow(zoom).roundToInt()
    val worldSizePx = worldTiles * tileSizePx
    val centerX = worldX(requireNotNull(model.centerLongitude), worldSizePx)
    val centerY = worldY(requireNotNull(model.centerLatitude), worldSizePx)
    val viewportLeft = centerX - viewportSize.width / 2.0
    val viewportTop = centerY - viewportSize.height / 2.0
    val firstTileX = floor(viewportLeft / tileSizePx).toInt()
    val lastTileX = floor((viewportLeft + viewportSize.width - 1) / tileSizePx).toInt()
    val firstTileY = floor(viewportTop / tileSizePx).toInt()
    val lastTileY = floor((viewportTop + viewportSize.height - 1) / tileSizePx).toInt()

    val bitmap = Bitmap.createBitmap(
        viewportSize.width,
        viewportSize.height,
        Bitmap.Config.ARGB_8888,
    )
    try {
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(238, 242, 245))
        val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var renderedTileCount = 0
        var expectedTileCount = 0
        for (y in firstTileY..lastTileY) {
            if (y in 0 until worldTiles) expectedTileCount += lastTileX - firstTileX + 1
        }

        // A late/failed tile must not discard already loaded streets and the saved route.
        // Each request is bounded too, so a stalled first tile cannot consume the whole budget.
        withTimeoutOrNull(tileBudgetMillis) {
            for (rawTileY in firstTileY..lastTileY) {
                if (rawTileY !in 0 until worldTiles) continue
                for (rawTileX in firstTileX..lastTileX) {
                    currentCoroutineContext().ensureActive()
                    val tileX = Math.floorMod(rawTileX, worldTiles)
                    withTimeoutOrNull(2_500) tileRequest@{
                        // Keep the bitmap's complete lifetime inside the timeout block. Returning
                        // it across a cancellable boundary could lose ownership and leak pixels.
                        val tile = loadTile(zoom, tileX, rawTileY) ?: return@tileRequest
                        try {
                            val left = (rawTileX * tileSizePx - viewportLeft).toFloat()
                            val top = (rawTileY * tileSizePx - viewportTop).toFloat()
                            canvas.drawBitmap(
                                tile,
                                null,
                                RectF(left, top, left + tileSizePx.toFloat(), top + tileSizePx.toFloat()),
                                tilePaint,
                            )
                            renderedTileCount += 1
                        } finally {
                            // Tiles are not shared or cached as bitmaps; release them after copying.
                            if (!tile.isRecycled) tile.recycle()
                        }
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        if (renderedTileCount == 0) {
            throw IOException("OpenStreetMap tiles are unavailable")
        }

        drawRoute(
            canvas = canvas,
            model = model,
            viewportSize = viewportSize,
            centerX = centerX,
            worldSizePx = worldSizePx,
            viewportLeft = viewportLeft,
            viewportTop = viewportTop,
            density = safeDensity,
        )
        drawAttribution(canvas, viewportSize, safeDensity, (context.applicationContext as DailyBeatApp).mapSettings.state.value.provider.attribution)
        return JourneyMapRaster(bitmap, complete = renderedTileCount == expectedTileCount)
    } catch (error: Throwable) {
        // Cancellation while a card scrolls off screen used to strand a partially rendered
        // bitmap until a future GC. A fast scroll through Days could therefore spike memory.
        if (!bitmap.isRecycled) bitmap.recycle()
        throw error
    }
}

private fun drawRoute(
    canvas: Canvas,
    model: JourneyMapModel,
    viewportSize: IntSize,
    centerX: Double,
    worldSizePx: Double,
    viewportLeft: Double,
    viewportTop: Double,
    density: Float,
) {
    val casingPaint = routePaint(JOURNEY_ROUTE_CASING_COLOR, 8f * density)
    val routePaint = routePaint(JOURNEY_ROUTE_COLOR, 5f * density)
    model.routeSegments.filter { it.size >= 2 }.forEach { segment ->
        val path = Path()
        segment.forEachIndexed { index, point ->
            val x = alignedWorldX(point.longitude, centerX, worldSizePx) - viewportLeft
            val y = worldY(point.latitude, worldSizePx) - viewportTop
            if (index == 0) path.moveTo(x.toFloat(), y.toFloat())
            else path.lineTo(x.toFloat(), y.toFloat())
        }
        canvas.drawPath(path, casingPaint)
        canvas.drawPath(path, routePaint)
    }
    val gapPaint = routePaint("#64748B", 4f * density).apply {
        pathEffect = DashPathEffect(floatArrayOf(7f * density, 6f * density), 0f)
    }
    model.gapSegments.forEach { segment ->
        val path = Path()
        segment.forEachIndexed { index, point ->
            val x = alignedWorldX(point.longitude, centerX, worldSizePx) - viewportLeft
            val y = worldY(point.latitude, worldSizePx) - viewportTop
            if (index == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        canvas.drawPath(path, gapPaint)
    }

    val stopOuterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(JOURNEY_STOP_STROKE_COLOR)
        style = Paint.Style.FILL
    }
    val stopInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor(JOURNEY_STOP_COLOR)
        style = Paint.Style.FILL
    }
    model.stopPoints.forEach { point ->
        val x = (alignedWorldX(point.longitude, centerX, worldSizePx) - viewportLeft).toFloat()
        val y = (worldY(point.latitude, worldSizePx) - viewportTop).toFloat()
        canvas.drawCircle(x, y, 7f * density, stopOuterPaint)
        canvas.drawCircle(x, y, 4.5f * density, stopInnerPaint)
    }
    drawStopLabels(
        canvas = canvas,
        points = model.labeledStopPoints,
        viewportSize = viewportSize,
        centerX = centerX,
        worldSizePx = worldSizePx,
        viewportLeft = viewportLeft,
        viewportTop = viewportTop,
        density = density,
    )
}

private fun drawStopLabels(
    canvas: Canvas,
    points: List<JourneyPoint>,
    viewportSize: IntSize,
    centerX: Double,
    worldSizePx: Double,
    viewportLeft: Double,
    viewportTop: Double,
    density: Float,
) {
    if (points.isEmpty()) return
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * density
    }
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6172630")
        style = Paint.Style.FILL
    }
    val horizontalPadding = 7f * density
    val verticalPadding = 5f * density
    val margin = 8f * density
    val markerGap = 10f * density
    val availableLabelWidth = (viewportSize.width - margin * 2).coerceAtLeast(1f)
    val maximumTextWidth = minOf(
        160f * density,
        (availableLabelWidth - horizontalPadding * 2).coerceAtLeast(1f),
    )
    val metrics = textPaint.fontMetrics
    val labelHeight = metrics.descent - metrics.ascent + verticalPadding * 2

    points.forEachIndexed { index, point ->
        val place = point.stopLabel.orEmpty()
        val duration = com.dailybeat.app.util.Formatters.durationCompact(point.stopDurationMinutes)
        val text = fitMapLabel("$place · $duration", textPaint, maximumTextWidth)
        val textWidth = textPaint.measureText(text)
        val markerX = (alignedWorldX(point.longitude, centerX, worldSizePx) - viewportLeft).toFloat()
        val markerY = (worldY(point.latitude, worldSizePx) - viewportTop).toFloat()
        val labelWidth = (textWidth + horizontalPadding * 2).coerceAtMost(availableLabelWidth)
        val maximumLeft = (viewportSize.width - margin - labelWidth).coerceAtLeast(margin)
        val left = (markerX - labelWidth / 2).coerceIn(margin, maximumLeft)
        val preferBelow = markerY - markerGap - labelHeight < margin || index % 2 == 1
        val top = if (preferBelow) markerY + markerGap else markerY - markerGap - labelHeight
        val maximumTop = (viewportSize.height - margin - labelHeight).coerceAtLeast(margin)
        val safeTop = top.coerceIn(margin, maximumTop)
        val background = RectF(
            left,
            safeTop,
            left + labelWidth,
            safeTop + labelHeight,
        )
        canvas.drawRoundRect(background, 7f * density, 7f * density, backgroundPaint)
        canvas.drawText(
            text,
            background.left + horizontalPadding,
            background.top + verticalPadding - metrics.ascent,
            textPaint,
        )
    }
}

private fun fitMapLabel(text: String, paint: Paint, maximumWidth: Float): String {
    if (paint.measureText(text) <= maximumWidth) return text
    val ellipsis = "…"
    val available = (maximumWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
    val characters = paint.breakText(text, true, available, null).coerceAtLeast(0)
    return text.take(characters).trimEnd() + ellipsis
}

private fun routePaint(colorValue: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor(colorValue)
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun drawAttribution(canvas: Canvas, viewportSize: IntSize, density: Float, attribution: String) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 50, 62)
        textSize = 9f * density
    }
    val horizontalPadding = 5f * density
    val verticalPadding = 3f * density
    val margin = 4f * density
    val textWidth = textPaint.measureText(attribution)
    val metrics = textPaint.fontMetrics
    val textHeight = metrics.descent - metrics.ascent
    val right = viewportSize.width - margin
    val bottom = viewportSize.height - margin
    val background = RectF(
        right - textWidth - horizontalPadding * 2,
        bottom - textHeight - verticalPadding * 2,
        right,
        bottom,
    )
    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDDFDFDFD.toInt() }
    canvas.drawRoundRect(background, 4f * density, 4f * density, backgroundPaint)
    canvas.drawText(
        attribution,
        background.left + horizontalPadding,
        background.top + verticalPadding - metrics.ascent,
        textPaint,
    )
}

private fun worldX(longitude: Double, worldSizePx: Double): Double =
    (longitude + 180.0) / 360.0 * worldSizePx

private fun alignedWorldX(longitude: Double, centerX: Double, worldSizePx: Double): Double {
    var x = worldX(longitude, worldSizePx)
    while (x - centerX > worldSizePx / 2.0) x -= worldSizePx
    while (x - centerX < -worldSizePx / 2.0) x += worldSizePx
    return x
}

private fun worldY(latitude: Double, worldSizePx: Double): Double {
    val safeLatitude = latitude.coerceIn(
        -JourneyMapModel.WEB_MERCATOR_MAX_LATITUDE,
        JourneyMapModel.WEB_MERCATOR_MAX_LATITUDE,
    )
    val radians = Math.toRadians(safeLatitude)
    val mercator = ln(tan(radians) + 1.0 / cos(radians))
    return (1.0 - mercator / PI) / 2.0 * worldSizePx
}

private object OsmRasterTileClient {
    suspend fun load(context: Context, zoom: Int, x: Int, y: Int): Bitmap? {
        val app = context.applicationContext as DailyBeatApp
        val preferences = app.mapSettings.state.value
        if (!preferences.allowOnlineMaps) return null
        val request = Request.Builder()
            .url(preferences.provider.rasterTileTemplate.replace("{z}", zoom.toString())
                .replace("{x}", x.toString()).replace("{y}", y.toString()))
            .header("Accept", "image/png").build()
        val bytes = try {
            app.mapNetwork.tiles.newCall(request).awaitBoundedBytes(MAX_TILE_RESPONSE_BYTES)
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            return null
        }
        return decodeMapTile(bytes)
    }
}

internal fun decodeMapTile(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_TILE_DIMENSION_PX || bounds.outHeight !in 1..MAX_TILE_DIMENSION_PX ||
            bounds.outWidth.toLong() * bounds.outHeight.toLong() > MAX_TILE_PIXELS) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
