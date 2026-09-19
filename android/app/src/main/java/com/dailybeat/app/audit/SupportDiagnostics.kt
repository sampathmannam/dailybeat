package com.dailybeat.app.audit

import android.os.Build
import com.dailybeat.app.BuildConfig
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.capture.MotionStateStore
import com.dailybeat.app.util.PermissionHelper
import org.json.JSONObject

/** A fixed allowlist, not a log dump. Generated locally only after the user asks. */
object SupportDiagnostics {
    fun build(app: DailyBeatApp): String = JSONObject().apply {
        put("appVersion", BuildConfig.VERSION_NAME)
        put("androidApi", Build.VERSION.SDK_INT)
        put("locationBackend", if (BuildConfig.GOOGLE_LOCATION) "google" else "platform")
        put("captureEnabled", app.settingsRepository.get().gpsCaptureEnabled)
        put("capturePaused", app.settingsRepository.isCapturePaused())
        put("captureRunning", LocationService.isRunning)
        put("motionWatcherArmed", MotionStateStore(app).watcherArmed)
        put("locationPermission", PermissionHelper.hasLocation(app))
        put("backgroundLocationPermission", PermissionHelper.hasBackgroundLocation(app))
        put("notificationPermission", PermissionHelper.hasNotifications(app))
        put("recentFailureCount", OperationalFailureLog.readRecent(app, 80).size)
        put("containsPersonalRecords", false)
    }.toString(2)
}
