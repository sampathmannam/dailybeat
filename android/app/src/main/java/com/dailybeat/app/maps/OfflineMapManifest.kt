package com.dailybeat.app.maps

import org.json.JSONObject

data class MapPart(val name: String, val url: String, val bytes: Long, val sha256: String)

data class OfflineMapManifest(
    val version: String,
    val dataDate: String,
    val bounds: List<Double>,
    val tiles: List<MapPart>,
    val tileBytes: Long,
    val tileSha256: String,
    val assets: MapPart,
    val unpackedAssetBytes: Long,
) {
    val downloadBytes: Long get() = tileBytes + assets.bytes
    fun contains(latitude: Double, longitude: Double): Boolean =
        longitude in bounds[0]..bounds[2] && latitude in bounds[1]..bounds[3]

    companion object {
        fun parse(text: String): OfflineMapManifest {
            require(text.length <= 64 * 1024) { "Map catalog is too large." }
            val json = JSONObject(text)
            require(json.getInt("schema") == 1 && json.getString("region") == "tamil-nadu")
            fun part(item: JSONObject): MapPart {
                val name = item.getString("name")
                val url = item.getString("url")
                val bytes = item.getLong("bytes")
                val hash = item.getString("sha256")
                require(name.matches(Regex("[A-Za-z0-9._-]{1,100}")) && !name.contains(".."))
                require(url.startsWith("https://github.com/sampathmannam/dailybeat/releases/download/v") &&
                    url.substringAfterLast('/') == name && !url.contains('?') && !url.contains('#'))
                require(bytes in 1..536_870_912L && hash.matches(Regex("[a-f0-9]{64}")))
                return MapPart(name, url, bytes, hash)
            }
            val tiles = json.getJSONArray("tiles").let { array ->
                require(array.length() in 1..32)
                (0 until array.length()).map { part(array.getJSONObject(it)) }
            }
            val bounds = json.getJSONArray("bounds").let { array ->
                require(array.length() == 4)
                (0..3).map { array.getDouble(it) }
            }
            require(bounds.all { it.isFinite() } && bounds[0] in -180.0..180.0 &&
                bounds[2] in bounds[0]..180.0 && bounds[1] in -85.0..85.0 && bounds[3] in bounds[1]..85.0)
            val version = json.getString("version")
            require(version.matches(Regex("[a-zA-Z0-9_-]{1,60}")))
            val tileBytes = json.getLong("tileBytes")
            require(tileBytes == tiles.sumOf { it.bytes })
            val tileHash = json.getString("tileSha256")
            require(tileHash.matches(Regex("[a-f0-9]{64}")))
            val unpacked = json.getLong("unpackedAssetBytes")
            require(unpacked in 1..268_435_456L)
            return OfflineMapManifest(version, json.getString("dataDate"), bounds, tiles, tileBytes,
                tileHash, part(json.getJSONObject("assets")), unpacked)
        }
    }
}
