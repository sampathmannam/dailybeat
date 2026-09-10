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
import com.dailybeat.app.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Cache
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TILE_SIZE_LOGICAL_PX = 256.0
private const val MAP_PADDING_LOGICAL_PX = 40
private const val TILE_CACHE_BYTES = 64L * 1024L * 1024L
private const val MAX_TILE_RESPONSE_BYTES = 2L * 1024L * 1024L
private const val ATTRIBUTION = "© OpenStreetMap contributors"

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
): Bitmap {
    require(viewportSize.width > 0 && viewportSize.height > 0)
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
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(238, 242, 245))
    val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var renderedTileCount = 0

    for (rawTileY in firstTileY..lastTileY) {
        if (rawTileY !in 0 until worldTiles) continue
        for (rawTileX in firstTileX..lastTileX) {
            currentCoroutineContext().ensureActive()
            val tileX = Math.floorMod(rawTileX, worldTiles)
            val tile = OsmRasterTileClient.load(context, zoom, tileX, rawTileY) ?: continue
            val left = (rawTileX * tileSizePx - viewportLeft).toFloat()
            val top = (rawTileY * tileSizePx - viewportTop).toFloat()
            canvas.drawBitmap(
                tile,
                null,
                RectF(left, top, left + tileSizePx.toFloat(), top + tileSizePx.toFloat()),
                tilePaint,
            )
            renderedTileCount += 1
        }
    }
    if (renderedTileCount == 0) {
        bitmap.recycle()
        throw IOException("OpenStreetMap tiles are unavailable")
    }

    drawRoute(
        canvas = canvas,
        model = model,
        centerX = centerX,
        worldSizePx = worldSizePx,
        viewportLeft = viewportLeft,
        viewportTop = viewportTop,
        density = safeDensity,
    )
    drawAttribution(canvas, viewportSize, safeDensity)
    return bitmap
}

private fun drawRoute(
    canvas: Canvas,
    model: JourneyMapModel,
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
}

private fun routePaint(colorValue: String, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.parseColor(colorValue)
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

private fun drawAttribution(canvas: Canvas, viewportSize: IntSize, density: Float) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 50, 62)
        textSize = 9f * density
    }
    val horizontalPadding = 5f * density
    val verticalPadding = 3f * density
    val margin = 4f * density
    val textWidth = textPaint.measureText(ATTRIBUTION)
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
        ATTRIBUTION,
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
    private val lock = Any()
    @Volatile private var sharedClient: OkHttpClient? = null

    suspend fun load(context: Context, zoom: Int, x: Int, y: Int): Bitmap? {
        val request = Request.Builder()
            .url(
                JOURNEY_RASTER_TILE_URL_TEMPLATE
                    .replace("{z}", zoom.toString())
                    .replace("{x}", x.toString())
                    .replace("{y}", y.toString()),
            )
            .header(
                "User-Agent",
                "DailyBeat/${BuildConfig.VERSION_NAME} (+https://github.com/sampathmannam/dailybeat)",
            )
            .header("Accept", "image/png")
            .build()
        val call = client(context).newCall(request)
        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        return try {
            call.decodeBitmapResponse()
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            null
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private fun client(context: Context): OkHttpClient = sharedClient ?: synchronized(lock) {
        sharedClient ?: OkHttpClient.Builder()
            .cache(
                Cache(
                    directory = File(context.cacheDir, "osm-journey-map-tiles"),
                    maxSize = TILE_CACHE_BYTES,
                ),
            )
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
            .also { sharedClient = it }
    }

    private fun Call.decodeBitmapResponse(): Bitmap? = execute().use { response ->
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        val contentLength = body.contentLength()
        if (contentLength > MAX_TILE_RESPONSE_BYTES) return null
        val bytes = body.bytes()
        if (bytes.size > MAX_TILE_RESPONSE_BYTES) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
