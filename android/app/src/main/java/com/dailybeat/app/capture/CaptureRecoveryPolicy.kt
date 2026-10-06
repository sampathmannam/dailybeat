package com.dailybeat.app.capture

/** Monotonic-time recovery: a bounded burst, followed by a cooldown when signal stays poor. */
class CaptureRecoveryPolicy(startedAtElapsedMs: Long) {
    enum class Action { NONE, START_BURST, END_BURST }
    private var lastAcceptedAt = startedAtElapsedMs
    private var burstStartedAt: Long? = null
    private var nextAttemptAt = startedAtElapsedMs + STALE_AFTER_MS

    @Synchronized
    fun accepted(atElapsedMs: Long) {
        lastAcceptedAt = maxOf(lastAcceptedAt, atElapsedMs)
    }

    @Synchronized
    fun tick(nowElapsedMs: Long): Action {
        val burst = burstStartedAt
        if (burst != null) {
            if (lastAcceptedAt > burst || nowElapsedMs - burst >= MAX_BURST_MS) {
                burstStartedAt = null
                nextAttemptAt = nowElapsedMs + COOLDOWN_MS
                return Action.END_BURST
            }
        } else if (nowElapsedMs - lastAcceptedAt >= STALE_AFTER_MS && nowElapsedMs >= nextAttemptAt) {
            burstStartedAt = nowElapsedMs
            return Action.START_BURST
        }
        return Action.NONE
    }

    companion object {
        const val STALE_AFTER_MS = 3 * 60_000L
        const val MAX_BURST_MS = 90_000L
        const val COOLDOWN_MS = 5 * 60_000L
        const val CHECK_INTERVAL_MS = 15_000L
    }
}
