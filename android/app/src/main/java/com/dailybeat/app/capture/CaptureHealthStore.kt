package com.dailybeat.app.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CaptureHealth(
    val serviceRunning: Boolean = false,
    val serviceStartedAtMs: Long = 0,
    val lastFixAtMs: Long = 0,
    val lastStoredAtMs: Long = 0,
    val lastAccuracyM: Float? = null,
    val lastQuality: String? = null,
    val lastRejectionReason: String? = null,
    val rejectedCountToday: Int = 0,
)

enum class CaptureHealthLevel { OFF, WAITING, HEALTHY, DEGRADED }

data class CaptureHealthStatus(
    val level: CaptureHealthLevel,
    val lastPointAgeMs: Long? = null,
    val accuracyM: Float? = null,
    val rejectedCount: Int = 0,
    val reason: String? = null,
)

fun CaptureHealth.status(nowMs: Long, enabled: Boolean): CaptureHealthStatus {
    if (!enabled || !serviceRunning) {
        return CaptureHealthStatus(CaptureHealthLevel.OFF, reason = "Tracking is off")
    }
    if (lastStoredAtMs <= 0L) {
        return CaptureHealthStatus(CaptureHealthLevel.WAITING, rejectedCount = rejectedCountToday)
    }
    val age = (nowMs - lastStoredAtMs).coerceAtLeast(0)
    // A long gap is still just "no recent point", never an alarm telling the officer to go
    // troubleshoot: DailyBeat keeps everything it captured and keeps watching. OFF (below) is the
    // only state that legitimately calls for action, because capture genuinely is not running.
    val level = if (age <= CaptureHealthStore.HEALTHY_AGE_MS) {
        CaptureHealthLevel.HEALTHY
    } else {
        CaptureHealthLevel.DEGRADED
    }
    return CaptureHealthStatus(
        level = level,
        lastPointAgeMs = age,
        accuracyM = lastAccuracyM,
        rejectedCount = rejectedCountToday,
        reason = lastRejectionReason,
    )
}

class CaptureHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _health = MutableStateFlow(read())
    val health: StateFlow<CaptureHealth> = _health.asStateFlow()

    @Synchronized
    fun serviceStarted(nowMs: Long = System.currentTimeMillis()) = update {
        it.copy(serviceRunning = true, serviceStartedAtMs = nowMs)
    }

    @Synchronized
    fun serviceStopped() = update { it.copy(serviceRunning = false) }

    @Synchronized
    fun fixObserved(timestampMs: Long) = update { it.copy(lastFixAtMs = timestampMs) }

    @Synchronized
    fun accepted(sample: LocationSample, quality: String) = update { current ->
        current.copy(
            lastFixAtMs = sample.timestampMs,
            lastStoredAtMs = sample.timestampMs,
            lastAccuracyM = sample.accuracyM,
            lastQuality = quality,
            lastRejectionReason = null,
        )
    }

    @Synchronized
    fun rejected(timestampMs: Long, reason: String) = update { current ->
        val todayKey = dayKey(timestampMs)
        val storedDay = prefs.getString(KEY_REJECTION_DAY, null)
        val count = if (storedDay == todayKey) current.rejectedCountToday + 1 else 1
        prefs.edit().putString(KEY_REJECTION_DAY, todayKey).apply()
        current.copy(
            lastFixAtMs = timestampMs,
            lastRejectionReason = reason,
            rejectedCountToday = count,
        )
    }

    private fun update(block: (CaptureHealth) -> CaptureHealth) {
        val next = block(_health.value)
        prefs.edit()
            .putBoolean(KEY_RUNNING, next.serviceRunning)
            .putLong(KEY_STARTED, next.serviceStartedAtMs)
            .putLong(KEY_LAST_FIX, next.lastFixAtMs)
            .putLong(KEY_LAST_STORED, next.lastStoredAtMs)
            .putFloat(KEY_ACCURACY, next.lastAccuracyM ?: -1f)
            .putString(KEY_QUALITY, next.lastQuality)
            .putString(KEY_REJECTION, next.lastRejectionReason)
            .putInt(KEY_REJECTION_COUNT, next.rejectedCountToday)
            .apply()
        _health.value = next
    }

    private fun read(): CaptureHealth {
        val accuracy = prefs.getFloat(KEY_ACCURACY, -1f).takeIf { it >= 0f }
        val lastStored = prefs.getLong(KEY_LAST_STORED, 0)
        val todayRejections = if (
            prefs.getString(KEY_REJECTION_DAY, null) == dayKey(System.currentTimeMillis())
        ) prefs.getInt(KEY_REJECTION_COUNT, 0) else 0
        return CaptureHealth(
            serviceRunning = false,
            serviceStartedAtMs = prefs.getLong(KEY_STARTED, 0),
            lastFixAtMs = prefs.getLong(KEY_LAST_FIX, 0),
            lastStoredAtMs = lastStored,
            lastAccuracyM = accuracy,
            lastQuality = prefs.getString(KEY_QUALITY, null),
            lastRejectionReason = prefs.getString(KEY_REJECTION, null),
            rejectedCountToday = todayRejections,
        )
    }

    private fun dayKey(timestampMs: Long): String = java.time.Instant.ofEpochMilli(timestampMs)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()

    companion object {
        const val HEALTHY_AGE_MS = 5 * 60_000L
        private const val PREFS_NAME = "capture_health"
        private const val KEY_RUNNING = "service_running"
        private const val KEY_STARTED = "service_started_at"
        private const val KEY_LAST_FIX = "last_fix_at"
        private const val KEY_LAST_STORED = "last_stored_at"
        private const val KEY_ACCURACY = "last_accuracy"
        private const val KEY_QUALITY = "last_quality"
        private const val KEY_REJECTION = "last_rejection"
        private const val KEY_REJECTION_COUNT = "rejection_count"
        private const val KEY_REJECTION_DAY = "rejection_day"
    }
}
