package com.dailybeat.app.data.settings

import android.content.Context

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)

    fun get(): AppSettings = AppSettings(
        officerName = prefs.getString(KEY_OFFICER, "IPS Officer") ?: "IPS Officer",
        gpsCaptureEnabled = prefs.getBoolean(KEY_GPS, true),
        callLogEnabled = prefs.getBoolean(KEY_CALL_LOG, false),
    )

    fun setOfficerName(name: String) {
        prefs.edit().putString(KEY_OFFICER, name.trim()).apply()
    }

    fun setGpsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS, enabled).apply()
    }

    fun setCallLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_LOG, enabled).apply()
    }

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, complete).apply()
    }

    companion object {
        private const val KEY_OFFICER = "officer_name"
        private const val KEY_GPS = "gps_enabled"
        private const val KEY_CALL_LOG = "call_log_enabled"
        private const val KEY_ONBOARDING = "onboarding_complete"
    }
}
