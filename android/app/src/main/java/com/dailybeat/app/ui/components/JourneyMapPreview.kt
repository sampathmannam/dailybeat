package com.dailybeat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dailybeat.app.data.model.LocationVisit
import com.dailybeat.app.ui.theme.EventGps
import com.dailybeat.app.ui.theme.Gold

@Composable
fun JourneyMapPreview(
    visits: List<LocationVisit>,
    modifier: Modifier = Modifier,
) {
    if (visits.isEmpty()) return
    val dwells = visits.filter { it.visitType != "transit" }
    val pointCount = if (dwells.isNotEmpty()) dwells.size else visits.size

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            val dwells = visits.filter { it.visitType != "transit" }
            val points = if (dwells.isNotEmpty()) dwells else visits
            if (points.isEmpty()) return@Canvas

            val lats = points.map { it.latitude }
            val lons = points.map { it.longitude }
            val minLat = lats.min()
            val maxLat = lats.max()
            val minLon = lons.min()
            val maxLon = lons.max()
            val latSpan = (maxLat - minLat).coerceAtLeast(0.0005)
            val lonSpan = (maxLon - minLon).coerceAtLeast(0.0005)
            val pad = 24f

            fun toOffset(lat: Double, lon: Double): Offset {
                val x = pad + ((lon - minLon) / lonSpan).toFloat() * (size.width - pad * 2)
                val y = size.height - pad - ((lat - minLat) / latSpan).toFloat() * (size.height - pad * 2)
                return Offset(x, y)
            }

            val path = Path()
            points.forEachIndexed { index, visit ->
                val point = toOffset(visit.latitude, visit.longitude)
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, Gold, style = Stroke(width = 3f))

            points.forEach { visit ->
                val point = toOffset(visit.latitude, visit.longitude)
                val color = if (visit.visitType == "transit") Gold else EventGps
                drawCircle(color = color, radius = 8f, center = point)
            }
        }
        Text(
            text = "Journey map ($pointCount points)",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
