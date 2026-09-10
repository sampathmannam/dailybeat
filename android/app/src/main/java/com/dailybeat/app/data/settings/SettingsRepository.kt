package com.dailybeat.app.data.settings

import android.content.Context

class SettingsRepository(
    private val context: Context,
    val secureApiKey: SecureApiKeyStore = SecureApiKeyStore(context),
) {

    private val prefs = context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)

    fun get(): AppSettings = AppSettings(
        officerName = (prefs.getString(KEY_OFFICER, "IPS Officer") ?: "IPS Officer")
            .take(MAX_NAME_CHARS),
        gpsCaptureEnabled = prefs.getBoolean(KEY_GPS, true),
        cloudLlmEnabled = prefs.getBoolean(KEY_CLOUD_ENABLED, true),
        cloudProvider = (prefs.getString(KEY_CLOUD_PROVIDER, CloudProvider.DEEPSEEK.id)
            ?: CloudProvider.DEEPSEEK.id).let { stored ->
            CloudProvider.entries.find { it.id == stored }?.id ?: CloudProvider.DEEPSEEK.id
        },
        cloudModel = (prefs.getString(KEY_CLOUD_MODEL, CloudProvider.DEEPSEEK.defaultModel)
            ?: CloudProvider.DEEPSEEK.defaultModel).take(MAX_MODEL_CHARS),
        cloudBaseUrl = (prefs.getString(KEY_CLOUD_BASE_URL, "") ?: "").take(MAX_URL_CHARS),
        autoEveningReport = prefs.getBoolean(KEY_AUTO_REPORT, true),
        autoMiddayPulse = prefs.getBoolean(KEY_MIDDAY_PULSE, false),
        supervisorName = (prefs.getString(KEY_SUPERVISOR, "") ?: "").take(MAX_NAME_CHARS),
    )

    fun setOfficerName(name: String) {
        prefs.edit().putString(KEY_OFFICER, name.trim().take(MAX_NAME_CHARS)).apply()
    }

    fun setGpsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS, enabled).apply()
    }

    fun capturePausedUntilMs(nowMs: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_CAPTURE_PAUSED_UNTIL, 0L)
        if (until in 1..nowMs) {
            prefs.edit().remove(KEY_CAPTURE_PAUSED_UNTIL).apply()
            return 0L
        }
        return until
    }

    fun pauseCaptureUntil(timestampMs: Long) {
        prefs.edit().putLong(KEY_CAPTURE_PAUSED_UNTIL, timestampMs.coerceAtLeast(0L)).apply()
    }

    fun clearCapturePause() {
        prefs.edit().remove(KEY_CAPTURE_PAUSED_UNTIL).apply()
    }

    fun isCapturePaused(nowMs: Long = System.currentTimeMillis()): Boolean =
        capturePausedUntilMs(nowMs) > nowMs

    fun setCloudLlmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_ENABLED, enabled).apply()
    }

    fun setCloudProvider(providerId: String) {
        val safeProvider = CloudProvider.entries.find { it.id == providerId }?.id
            ?: CloudProvider.DEEPSEEK.id
        prefs.edit().putString(KEY_CLOUD_PROVIDER, safeProvider).apply()
    }

    fun setCloudModel(model: String) {
        prefs.edit().putString(KEY_CLOUD_MODEL, model.trim().take(MAX_MODEL_CHARS)).apply()
    }

    fun setCloudBaseUrl(url: String) {
        prefs.edit().putString(KEY_CLOUD_BASE_URL, url.trim().take(MAX_URL_CHARS)).apply()
    }

    fun setAutoEveningReport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REPORT, enabled).apply()
    }

    fun setSupervisorName(name: String) {
        prefs.edit().putString(KEY_SUPERVISOR, name.trim().take(MAX_NAME_CHARS)).apply()
    }

    fun setAutoMiddayPulse(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIDDAY_PULSE, enabled).apply()
    }

    fun isCloudBrainReady(): Boolean = get().cloudLlmEnabled && secureApiKey.hasApiKey()

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, complete).apply()
    }

    companion object {
        private const val KEY_OFFICER = "officer_name"
        private const val KEY_GPS = "gps_enabled"
        private const val KEY_CAPTURE_PAUSED_UNTIL = "capture_paused_until"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_CLOUD_ENABLED = "cloud_llm_enabled"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_CLOUD_MODEL = "cloud_model"
        private const val KEY_CLOUD_BASE_URL = "cloud_base_url"
        private const val KEY_AUTO_REPORT = "auto_evening_report"
        private const val KEY_MIDDAY_PULSE = "auto_midday_pulse"
        private const val KEY_SUPERVISOR = "supervisor_name"
        private const val MAX_NAME_CHARS = 120
        private const val MAX_MODEL_CHARS = 200
        private const val MAX_URL_CHARS = 2_048
    }
}
