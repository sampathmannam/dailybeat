package com.dailybeat.app.data.settings

import android.content.Context
import com.dailybeat.app.data.model.PatrolRole
import java.util.UUID

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
        activePatrolSessionId = prefs.getString(KEY_ACTIVE_PATROL_SESSION, null),
        activePatrolDeadlineMs = prefs.getLong(KEY_ACTIVE_PATROL_DEADLINE, 0L).takeIf { it > 0L },
        pendingPatrolCloseSessionId = prefs.getString(KEY_PENDING_PATROL_CLOSE_SESSION, null),
        pendingPatrolCloseMissionId = prefs.getString(KEY_PENDING_PATROL_CLOSE_MISSION, null),
        pendingPatrolCloseReason = prefs.getString(KEY_PENDING_PATROL_CLOSE_REASON, "completed") ?: "completed",
        pendingPatrolCloseEndedAtMs = prefs.getLong(KEY_PENDING_PATROL_CLOSE_ENDED_AT, 0L)
            .takeIf { it > 0L },
        patrolEvidenceOwnerId = prefs.getString(KEY_PATROL_EVIDENCE_OWNER, null),
        patrolCaptureError = prefs.getString(KEY_PATROL_CAPTURE_ERROR, null),
        patrolRetentionIncidentAtMs = prefs.getLong(KEY_PATROL_RETENTION_INCIDENT_AT, 0L)
            .takeIf { it > 0L },
        patrolRetentionDiscardedItemCount = prefs.getLong(
            KEY_PATROL_RETENTION_DISCARDED_COUNT,
            0L,
        ).coerceAtLeast(0L),
        patrolRetentionIncidentUnresolved = prefs.getBoolean(
            KEY_PATROL_RETENTION_INCIDENT_UNRESOLVED,
            false,
        ),
        patrolRetentionEnforcementFailureAtMs = prefs.getLong(
            KEY_PATROL_RETENTION_ENFORCEMENT_FAILURE_AT,
            0L,
        ).takeIf { it > 0L },
        patrolRetentionDeletionIntentCount = prefs.getInt(
            KEY_PATROL_RETENTION_DELETION_INTENT_COUNT,
            0,
        ).coerceAtLeast(0),
        patrolRetentionDeletionIntentAtMs = prefs.getLong(
            KEY_PATROL_RETENTION_DELETION_INTENT_AT,
            0L,
        ).takeIf { it > 0L },
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

    fun setActivePatrolSession(sessionId: String?) {
        prefs.edit().apply {
            if (sessionId == null) remove(KEY_ACTIVE_PATROL_SESSION)
            else putString(KEY_ACTIVE_PATROL_SESSION, sessionId)
        }.apply()
    }

    fun setActivePatrolDeadline(deadlineMs: Long?) {
        check(prefs.edit().apply {
            if (deadlineMs == null) remove(KEY_ACTIVE_PATROL_DEADLINE)
            else putLong(KEY_ACTIVE_PATROL_DEADLINE, deadlineMs)
        }.commit()) { "Unable to persist the active patrol deadline." }
    }

    fun setPendingPatrolClose(
        sessionId: String?,
        missionId: String?,
        reason: String = "completed",
        endedAtMs: Long = System.currentTimeMillis(),
    ) {
        require(
            reason in setOf(
                "completed",
                "relieved",
                "cancelled",
                "device_issue",
                "duty_window_ended",
            ),
        )
        check(prefs.edit().apply {
            if (sessionId == null || missionId == null) {
                remove(KEY_PENDING_PATROL_CLOSE_SESSION)
                remove(KEY_PENDING_PATROL_CLOSE_MISSION)
                remove(KEY_PENDING_PATROL_CLOSE_REASON)
                remove(KEY_PENDING_PATROL_CLOSE_ENDED_AT)
            } else {
                putString(KEY_PENDING_PATROL_CLOSE_SESSION, sessionId)
                putString(KEY_PENDING_PATROL_CLOSE_MISSION, missionId)
                putString(KEY_PENDING_PATROL_CLOSE_REASON, reason)
                putLong(KEY_PENDING_PATROL_CLOSE_ENDED_AT, endedAtMs)
            }
        }.commit()) { "Unable to persist the pending patrol close." }
    }

    fun setPatrolEvidenceOwner(userId: String?) {
        check(prefs.edit().apply {
            if (userId.isNullOrBlank()) remove(KEY_PATROL_EVIDENCE_OWNER)
            else putString(KEY_PATROL_EVIDENCE_OWNER, userId)
        }.commit()) { "Unable to persist the patrol evidence owner." }
    }

    fun setPatrolCaptureError(message: String?) {
        check(prefs.edit().apply {
            if (message.isNullOrBlank()) remove(KEY_PATROL_CAPTURE_ERROR)
            else putString(KEY_PATROL_CAPTURE_ERROR, message)
        }.commit()) { "Unable to persist the patrol capture state." }
    }

    /** Records aggregate deletion telemetry without storing mission, staff, or route data. */
    fun recordPatrolRetentionIncident(discardedItemCount: Int, occurredAtMs: Long): Boolean {
        require(discardedItemCount > 0)
        require(occurredAtMs > 0L)
        val previous = prefs.getLong(KEY_PATROL_RETENTION_DISCARDED_COUNT, 0L).coerceAtLeast(0L)
        val next = if (Long.MAX_VALUE - previous < discardedItemCount.toLong()) {
            Long.MAX_VALUE
        } else {
            previous + discardedItemCount
        }
        return prefs.edit()
            .putLong(KEY_PATROL_RETENTION_INCIDENT_AT, occurredAtMs)
            .putLong(KEY_PATROL_RETENTION_DISCARDED_COUNT, next)
            .putBoolean(KEY_PATROL_RETENTION_INCIDENT_UNRESOLVED, true)
            .commit()
    }

    /** Persists aggregate deletion intent before any protected store is changed. */
    fun beginPatrolRetentionDeletion(discardedItemCount: Int, occurredAtMs: Long): Boolean {
        require(discardedItemCount > 0)
        require(occurredAtMs > 0L)
        val existingCount = prefs.getInt(KEY_PATROL_RETENTION_DELETION_INTENT_COUNT, 0)
            .coerceAtLeast(0)
        val existingAt = prefs.getLong(KEY_PATROL_RETENTION_DELETION_INTENT_AT, 0L)
            .takeIf { it > 0L }
        return prefs.edit()
            .putInt(KEY_PATROL_RETENTION_DELETION_INTENT_COUNT, maxOf(existingCount, discardedItemCount))
            .putLong(KEY_PATROL_RETENTION_DELETION_INTENT_AT, existingAt ?: occurredAtMs)
            .commit()
    }

    /** Atomically resolves the journal, records the incident, and unblocks capture. */
    fun completePatrolRetentionDeletion(discardedItemCount: Int, occurredAtMs: Long): Boolean {
        require(discardedItemCount > 0)
        require(occurredAtMs > 0L)
        val previous = prefs.getLong(KEY_PATROL_RETENTION_DISCARDED_COUNT, 0L).coerceAtLeast(0L)
        val next = if (Long.MAX_VALUE - previous < discardedItemCount.toLong()) {
            Long.MAX_VALUE
        } else {
            previous + discardedItemCount
        }
        return prefs.edit()
            .putLong(KEY_PATROL_RETENTION_INCIDENT_AT, occurredAtMs)
            .putLong(KEY_PATROL_RETENTION_DISCARDED_COUNT, next)
            .putBoolean(KEY_PATROL_RETENTION_INCIDENT_UNRESOLVED, true)
            .remove(KEY_PATROL_RETENTION_DELETION_INTENT_COUNT)
            .remove(KEY_PATROL_RETENTION_DELETION_INTENT_AT)
            .remove(KEY_PATROL_RETENTION_ENFORCEMENT_FAILURE_AT)
            .commit()
    }

    fun recordPatrolRetentionEnforcementSuccess(): Boolean = prefs.edit()
        .remove(KEY_PATROL_RETENTION_ENFORCEMENT_FAILURE_AT)
        .commit()

    /** Hides the active warning only; aggregate time/count history remains on-device. */
    fun acknowledgePatrolRetentionIncident(): Boolean = prefs.edit()
        .putBoolean(KEY_PATROL_RETENTION_INCIDENT_UNRESOLVED, false)
        .commit()

    fun recordPatrolRetentionEnforcementFailure(occurredAtMs: Long): Boolean {
        require(occurredAtMs > 0L)
        return prefs.edit()
            .putLong(KEY_PATROL_RETENTION_ENFORCEMENT_FAILURE_AT, occurredAtMs)
            .commit()
    }

    fun installationId(): String {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing
        return UUID.randomUUID().toString().also {
            check(prefs.edit().putString(KEY_INSTALLATION_ID, it).commit()) {
                "Unable to persist the PatrolGrid installation id."
            }
        }
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

    fun acknowledgedPatrolGridPrivacyNoticeVersion(): Int =
        prefs.getInt(KEY_PATROLGRID_PRIVACY_NOTICE_VERSION, 0)

    fun acknowledgePatrolGridPrivacyNotice(version: Int): Boolean {
        require(version > 0) { "Privacy notice version must be positive." }
        return prefs.edit()
            .putInt(KEY_PATROLGRID_PRIVACY_NOTICE_VERSION, version)
            .commit()
    }

    fun isPatrolGridLocked(): Boolean = prefs.getBoolean(KEY_PATROLGRID_LOCKED, false)

    fun setPatrolGridLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_PATROLGRID_LOCKED, locked).commit()
    }

    fun patrolGridBackgroundedAtMs(): Long? =
        prefs.getLong(KEY_PATROLGRID_BACKGROUNDED_AT, 0L).takeIf { it > 0L }

    fun setPatrolGridBackgroundedAtMs(timestampMs: Long?) {
        prefs.edit().apply {
            if (timestampMs == null) remove(KEY_PATROLGRID_BACKGROUNDED_AT)
            else putLong(KEY_PATROLGRID_BACKGROUNDED_AT, timestampMs)
        }.commit()
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
        private const val KEY_ACTIVE_PATROL_SESSION = "active_patrol_session"
        private const val KEY_ACTIVE_PATROL_DEADLINE = "active_patrol_deadline_ms"
        private const val KEY_INSTALLATION_ID = "patrolgrid_installation_id"
        private const val KEY_PENDING_PATROL_CLOSE_SESSION = "pending_patrol_close_session"
        private const val KEY_PENDING_PATROL_CLOSE_MISSION = "pending_patrol_close_mission"
        private const val KEY_PENDING_PATROL_CLOSE_REASON = "pending_patrol_close_reason"
        private const val KEY_PENDING_PATROL_CLOSE_ENDED_AT = "pending_patrol_close_ended_at_ms"
        private const val KEY_PATROL_EVIDENCE_OWNER = "patrol_evidence_owner"
        private const val KEY_PATROL_CAPTURE_ERROR = "patrol_capture_error"
        private const val KEY_PATROL_RETENTION_INCIDENT_AT =
            "patrol_retention_incident_at_ms"
        private const val KEY_PATROL_RETENTION_DISCARDED_COUNT =
            "patrol_retention_discarded_item_count"
        private const val KEY_PATROL_RETENTION_INCIDENT_UNRESOLVED =
            "patrol_retention_incident_unresolved"
        private const val KEY_PATROL_RETENTION_ENFORCEMENT_FAILURE_AT =
            "patrol_retention_enforcement_failure_at_ms"
        private const val KEY_PATROL_RETENTION_DELETION_INTENT_COUNT =
            "patrol_retention_deletion_intent_count"
        private const val KEY_PATROL_RETENTION_DELETION_INTENT_AT =
            "patrol_retention_deletion_intent_at_ms"
        private const val KEY_PATROLGRID_PRIVACY_NOTICE_VERSION =
            "patrolgrid_privacy_notice_version"
        private const val KEY_PATROLGRID_LOCKED = "patrolgrid_locked"
        private const val KEY_PATROLGRID_BACKGROUNDED_AT = "patrolgrid_backgrounded_at_ms"
    }
}
