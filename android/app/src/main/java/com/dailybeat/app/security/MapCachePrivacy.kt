package com.dailybeat.app.security

import android.content.Context
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

/** Bounds and clears map tiles that can reveal the patrol area viewed on this device. */
object MapCachePrivacy {
    private const val MAX_AMBIENT_CACHE_BYTES = 5L * 1024L * 1024L

    fun configure(context: Context) {
        MapLibre.getInstance(context.applicationContext)
        OfflineManager.getInstance(context.applicationContext).setMaximumAmbientCacheSize(
            MAX_AMBIENT_CACHE_BYTES,
            IgnoreResult,
        )
    }

    fun clear(context: Context) {
        runCatching {
            MapLibre.getInstance(context.applicationContext)
            OfflineManager.getInstance(context.applicationContext).clearAmbientCache(IgnoreResult)
        }
    }

    private object IgnoreResult : OfflineManager.FileSourceCallback {
        override fun onSuccess() = Unit
        override fun onError(error: String) = Unit
    }
}
