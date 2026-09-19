package com.dailybeat.app.maps

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

data class MapProviderConfig(
    val styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    val rasterTileTemplate: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
    val attribution: String = "© OpenStreetMap contributors",
) {
    fun validated(): MapProviderConfig = apply {
        validateUrl(styleUrl)
        require(listOf("{z}", "{x}", "{y}").all { rasterTileTemplate.contains(it) }) {
            "Tile URL must include {z}, {x} and {y}."
        }
        validateUrl(rasterTileTemplate.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0"))
        require(attribution.isNotBlank() && attribution.length <= 160 && attribution.none { it.isISOControl() }) {
            "Enter attribution text of up to 160 characters."
        }
    }

    private fun validateUrl(value: String) {
        val uri = runCatching { URI(value) }.getOrNull()
        require(value.length <= 2048 && uri?.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.query == null) {
            "Use an HTTPS URL without credentials, query parameters or fragments."
        }
    }
}

data class MapPreferences(
    val allowOnlineMaps: Boolean = true,
    val provider: MapProviderConfig = MapProviderConfig(),
)

/** Device-local choices: deliberately absent from diary backup/restore. */
class MapSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("dailybeat_maps", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state: StateFlow<MapPreferences> = mutable.asStateFlow()
    var onChange: () -> Unit = {}

    fun setOnline(allowed: Boolean) = save(state.value.copy(allowOnlineMaps = allowed))
    fun setProvider(provider: MapProviderConfig) = save(state.value.copy(provider = provider.validated()))

    private fun save(value: MapPreferences) {
        check(prefs.edit().putBoolean("online", value.allowOnlineMaps)
            .putString("style", value.provider.styleUrl).putString("raster", value.provider.rasterTileTemplate)
            .putString("attribution", value.provider.attribution).commit()) { "Could not save map settings." }
        mutable.value = value
        onChange()
    }

    fun resetForErase() {
        check(prefs.edit().clear().putBoolean("online", false).commit()) { "Could not erase map settings." }
        mutable.value = MapPreferences(allowOnlineMaps = false)
        onChange()
    }

    private fun read(): MapPreferences {
        val defaults = MapProviderConfig()
        val provider = runCatching {
            MapProviderConfig(prefs.getString("style", defaults.styleUrl)!!,
                prefs.getString("raster", defaults.rasterTileTemplate)!!,
                prefs.getString("attribution", defaults.attribution)!!).validated()
        }.getOrDefault(defaults)
        return MapPreferences(prefs.getBoolean("online", true), provider)
    }
}
