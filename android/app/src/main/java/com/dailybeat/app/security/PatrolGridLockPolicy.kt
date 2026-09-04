package com.dailybeat.app.security

const val PATROLGRID_BACKGROUND_LOCK_TIMEOUT_MS = 5 * 60 * 1_000L

fun shouldLockPatrolGrid(
    hasSession: Boolean,
    explicitlyLocked: Boolean,
    backgroundedAtMs: Long?,
    nowMs: Long,
    timeoutMs: Long = PATROLGRID_BACKGROUND_LOCK_TIMEOUT_MS,
): Boolean {
    if (!hasSession) return false
    if (explicitlyLocked) return true
    val backgrounded = backgroundedAtMs ?: return false
    val elapsed = nowMs - backgrounded
    return elapsed < 0L || elapsed >= timeoutMs
}
