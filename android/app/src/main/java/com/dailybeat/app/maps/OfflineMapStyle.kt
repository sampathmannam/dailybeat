package com.dailybeat.app.maps

import org.json.JSONObject

/**
 * Improve the verified package's dark cartography only at display time. The immutable download,
 * its hashes, source data, URLs, sprites and layout remain unchanged. Light maps are untouched.
 */
internal fun prepareOfflineMapStyle(rawStyle: String, dark: Boolean): String {
    if (!dark) return rawStyle
    val style = JSONObject(rawStyle)
    val layers = style.optJSONArray("layers") ?: return rawStyle
    for (index in 0 until layers.length()) {
        val layer = layers.getJSONObject(index)
        val id = layer.optString("id")
        val type = layer.optString("type")
        val label = type == "symbol" && layer.optJSONObject("layout")?.has("text-field") == true
        val road = type == "line" && id.startsWith("roads_") && !id.contains("_casing")
        if (!label && !road) continue
        val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
        if (label) {
            // A dark halo separates labels from brighter roads, shields and water. These neutral
            // inks remain legible without changing the app's chosen yellow or carbon theme.
            paint.put("text-color", if (id in PRIMARY_PLACE_LABELS) "#DEDEDE" else "#C4C4C4")
            paint.put("text-halo-color", "#1F1F1F")
            paint.put("text-halo-width", 1.25)
            paint.put("text-opacity", 1)
        } else {
            // Preserve road widths, dashes, zoom thresholds and casings. The existing warm
            // journey overlay remains distinct from these neutral streets and rail lines.
            paint.put("line-color", when {
                id.contains("highway") -> "#A6A6A6"
                id.contains("major") -> "#929292"
                else -> "#808080"
            })
            paint.put("line-opacity", 1)
        }
    }
    return style.toString()
}

private val PRIMARY_PLACE_LABELS = setOf("places_locality", "places_region", "places_country")
