package com.dailybeat.app.data.settings

import android.content.Context
import com.dailybeat.app.util.InputPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(
    private val context: Context,
    val secureApiKey: SecureApiKeyStore = SecureApiKeyStore(context),
) {

    private val prefs = context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
    private val _themePreference = MutableStateFlow(readThemePreference())
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    fun get(): AppSettings = AppSettings(
        officerName = (prefs.getString(KEY_OFFICER, "") ?: "")
            .let { InputPolicy.singleLine(it, InputPolicy.PERSON_NAME_CHARS) },
        themePreference = readThemePreference(),
        gpsCaptureEnabled = prefs.getBoolean(KEY_GPS, true),
        cloudLlmEnabled = prefs.getBoolean(KEY_CLOUD_ENABLED, false),
        cloudProvider = (prefs.getString(KEY_CLOUD_PROVIDER, CloudProvider.DEEPSEEK.id)
            ?: CloudProvider.DEEPSEEK.id).let { stored ->
            CloudProvider.entries.find { it.id == stored }?.id ?: CloudProvider.DEEPSEEK.id
        },
        cloudModel = (prefs.getString(KEY_CLOUD_MODEL, CloudProvider.DEEPSEEK.defaultModel)
            ?: CloudProvider.DEEPSEEK.defaultModel).let {
            InputPolicy.singleLine(it, InputPolicy.CLOUD_MODEL_CHARS)
        },
        cloudBaseUrl = (prefs.getString(KEY_CLOUD_BASE_URL, "") ?: "").let {
            InputPolicy.singleLine(it, InputPolicy.CLOUD_URL_CHARS)
        },
        autoEveningReport = prefs.getBoolean(KEY_AUTO_REPORT, false) &&
            prefs.getBoolean("auto_report_privacy_v1", false),
        autoMiddayPulse = prefs.getBoolean(KEY_MIDDAY_PULSE, false),
        supervisorName = (prefs.getString(KEY_SUPERVISOR, "") ?: "").let {
            InputPolicy.singleLine(it, InputPolicy.PERSON_NAME_CHARS)
        },
        journalProfile = readJournalProfile(),
    )

    private fun readJournalProfile(): JournalProfile {
        if (prefs.contains(KEY_PROFILE)) return JournalProfile.fromId(prefs.getString(KEY_PROFILE, null))
        // Existing police users retain their template. New installs start with a general journal.
        return if (prefs.contains(KEY_OFFICER) || isOnboardingComplete()) JournalProfile.POLICE
        else JournalProfile.PERSONAL
    }

    fun setJournalProfile(profile: JournalProfile) {
        prefs.edit().putString(KEY_PROFILE, profile.id).apply()
    }

    fun setOfficerName(name: String) {
        prefs.edit().putString(
            KEY_OFFICER,
            InputPolicy.singleLine(name, InputPolicy.PERSON_NAME_CHARS).trim(),
        ).apply()
    }

    fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString(KEY_THEME_PREFERENCE, preference.id).apply()
        _themePreference.value = preference
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
        prefs.edit().putString(
            KEY_CLOUD_MODEL,
            InputPolicy.singleLine(model, InputPolicy.CLOUD_MODEL_CHARS).trim(),
        ).apply()
    }

    fun setCloudBaseUrl(url: String) {
        prefs.edit().putString(
            KEY_CLOUD_BASE_URL,
            InputPolicy.singleLine(url, InputPolicy.CLOUD_URL_CHARS).trim(),
        ).apply()
    }

    fun setAutoEveningReport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REPORT, enabled)
            .putBoolean("auto_report_privacy_v1", true).apply()
    }

    fun setSupervisorName(name: String) {
        prefs.edit().putString(
            KEY_SUPERVISOR,
            InputPolicy.singleLine(name, InputPolicy.PERSON_NAME_CHARS).trim(),
        ).apply()
    }

    fun setAutoMiddayPulse(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MIDDAY_PULSE, enabled).apply()
    }

    fun isCloudBrainReady(): Boolean = get().cloudLlmEnabled && secureApiKey.hasApiKey()

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_ONBOARDING, false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, complete).apply()
    }

    private fun readThemePreference(): ThemePreference = ThemePreference.fromId(
        prefs.getString(KEY_THEME_PREFERENCE, ThemePreference.SYSTEM.id),
    )

    companion object {
        private const val KEY_PROFILE = "journal_profile"
        private const val KEY_OFFICER = "officer_name"
        private const val KEY_THEME_PREFERENCE = "theme_preference"
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
    }
}
