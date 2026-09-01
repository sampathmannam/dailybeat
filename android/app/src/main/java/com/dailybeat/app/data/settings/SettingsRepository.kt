package com.dailybeat.app.data.settings

import android.content.Context
import com.dailybeat.app.data.model.PatrolRole

class SettingsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
    val secureApiKey = SecureApiKeyStore(context)

    fun get(): AppSettings = AppSettings(
        officerName = prefs.getString(KEY_OFFICER, "IPS Officer") ?: "IPS Officer",
        gpsCaptureEnabled = prefs.getBoolean(KEY_GPS, false),
        callLogEnabled = prefs.getBoolean(KEY_CALL_LOG, false),
        cloudLlmEnabled = prefs.getBoolean(KEY_CLOUD_ENABLED, true),
        cloudProvider = prefs.getString(KEY_CLOUD_PROVIDER, CloudProvider.DEEPSEEK.id) ?: CloudProvider.DEEPSEEK.id,
        cloudModel = prefs.getString(KEY_CLOUD_MODEL, CloudProvider.DEEPSEEK.defaultModel)
            ?: CloudProvider.DEEPSEEK.defaultModel,
        cloudBaseUrl = prefs.getString(KEY_CLOUD_BASE_URL, "") ?: "",
        autoEveningReport = prefs.getBoolean(KEY_AUTO_REPORT, true),
        autoMiddayPulse = prefs.getBoolean(KEY_MIDDAY_PULSE, false),
        supervisorName = prefs.getString(KEY_SUPERVISOR, "") ?: "",
        patrolRole = PatrolRole.fromStorage(prefs.getString(KEY_PATROL_ROLE, null)),
        activePatrolMissionId = prefs.getString(KEY_ACTIVE_PATROL_MISSION, null),
    )

    fun setOfficerName(name: String) {
        prefs.edit().putString(KEY_OFFICER, name.trim()).apply()
    }

    fun setGpsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GPS, enabled).apply()
    }

    fun setPatrolRole(role: PatrolRole) {
        prefs.edit().putString(KEY_PATROL_ROLE, role.storageValue).apply()
    }

    fun setActivePatrolMission(missionId: String?) {
        prefs.edit().apply {
            if (missionId == null) remove(KEY_ACTIVE_PATROL_MISSION)
            else putString(KEY_ACTIVE_PATROL_MISSION, missionId)
        }.apply()
    }

    fun setCallLogEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_LOG, enabled).apply()
    }

    fun setCloudLlmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_ENABLED, enabled).apply()
    }

    fun setCloudProvider(providerId: String) {
        prefs.edit().putString(KEY_CLOUD_PROVIDER, providerId).apply()
    }

    fun setCloudModel(model: String) {
        prefs.edit().putString(KEY_CLOUD_MODEL, model.trim()).apply()
    }

    fun setCloudBaseUrl(url: String) {
        prefs.edit().putString(KEY_CLOUD_BASE_URL, url.trim()).apply()
    }

    fun setAutoEveningReport(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REPORT, enabled).apply()
    }

    fun setSupervisorName(name: String) {
        prefs.edit().putString(KEY_SUPERVISOR, name.trim()).apply()
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
        private const val KEY_CALL_LOG = "call_log_enabled"
        private const val KEY_ONBOARDING = "onboarding_complete"
        private const val KEY_CLOUD_ENABLED = "cloud_llm_enabled"
        private const val KEY_CLOUD_PROVIDER = "cloud_provider"
        private const val KEY_CLOUD_MODEL = "cloud_model"
        private const val KEY_CLOUD_BASE_URL = "cloud_base_url"
        private const val KEY_AUTO_REPORT = "auto_evening_report"
        private const val KEY_MIDDAY_PULSE = "auto_midday_pulse"
        private const val KEY_SUPERVISOR = "supervisor_name"
        private const val KEY_PATROL_ROLE = "patrol_role"
        private const val KEY_ACTIVE_PATROL_MISSION = "active_patrol_mission"
    }
}
