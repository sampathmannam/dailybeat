package com.dailybeat.app.util

import android.content.Context
import android.location.Location
import com.dailybeat.app.capture.LocationBackend

/** One bounded fresh fix through the selected build's provider; cancellation releases the request. */
suspend fun fetchCurrentLocation(context: Context): Location? {
    if (!PermissionHelper.hasLocation(context)) return null
    return try {
        LocationBackend(context.applicationContext).current()
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalStateException) {
        null
    }
}
