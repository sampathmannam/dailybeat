package com.dailybeat.app.data.settings

import com.dailybeat.app.data.model.PatrolRole

data class AppSettings(
    val officerName: String = "IPS Officer",
    val gpsCaptureEnabled: Boolean = false,
    val callLogEnabled: Boolean = false,
    val cloudLlmEnabled: Boolean = true,
    val cloudProvider: String = CloudProvider.DEEPSEEK.id,
    val cloudModel: String = CloudProvider.DEEPSEEK.defaultModel,
    val cloudBaseUrl: String = "",
    val autoEveningReport: Boolean = true,
    val autoMiddayPulse: Boolean = false,
    val supervisorName: String = "",
    val patrolRole: PatrolRole = PatrolRole.PATROL,
    val activePatrolMissionId: String? = null,
    val activePatrolSessionId: String? = null,
    val activePatrolDeadlineMs: Long? = null,
    val pendingPatrolCloseSessionId: String? = null,
    val pendingPatrolCloseMissionId: String? = null,
    val pendingPatrolCloseReason: String = "completed",
    val pendingPatrolCloseEndedAtMs: Long? = null,
    val patrolEvidenceOwnerId: String? = null,
    val patrolCaptureError: String? = null,
    /** Aggregate-only evidence-integrity signal; it contains no mission or staff id. */
    val patrolRetentionIncidentAtMs: Long? = null,
    val patrolRetentionDiscardedItemCount: Long = 0L,
    val patrolRetentionIncidentUnresolved: Boolean = false,
    val patrolRetentionEnforcementFailureAtMs: Long? = null,
    /** Aggregate crash-recovery journal; deliberately contains no mission or staff id. */
    val patrolRetentionDeletionIntentCount: Int = 0,
    val patrolRetentionDeletionIntentAtMs: Long? = null,
)

enum class CloudProvider(val id: String, val displayName: String, val defaultModel: String) {
    DEEPSEEK("deepseek", "DeepSeek", "deepseek-chat"),
    OPENAI("openai", "OpenAI", "gpt-4o-mini"),
    ANTHROPIC("anthropic", "Anthropic", "claude-3-5-haiku-20241022"),
    COMPATIBLE("compatible", "Compatible API", "gpt-4o-mini"),
}
