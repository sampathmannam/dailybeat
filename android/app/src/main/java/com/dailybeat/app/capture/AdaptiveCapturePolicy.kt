package com.dailybeat.app.capture

/**
 * The two requests DailyBeat uses while it is actively running a location foreground service.
 *
 * MOVING deliberately retains the production v4.0.6 request: it is already a good whole-day
 * route request and avoids sustained high-accuracy GPS. SETTLING is used only for the short
 * period after Android detects stillness, before the service shuts down and the motion watcher
 * takes over. Keeping this policy Android-free makes the battery contract straightforward to
 * regression-test.
 */
enum class ActiveCaptureProfile(
    val intervalMs: Long,
    val minIntervalMs: Long,
    val minDistanceM: Float,
    val maxDelayMs: Long,
) {
    MOVING(
        intervalMs = 45_000L,
        minIntervalMs = 45_000L,
        minDistanceM = 75f,
        maxDelayMs = 120_000L,
    ),
    SETTLING(
        intervalMs = 120_000L,
        minIntervalMs = 120_000L,
        minDistanceM = 100f,
        maxDelayMs = 600_000L,
    ),
}

/** Pure policy boundaries; Android callbacks and database writes live elsewhere. */
object AdaptiveCapturePolicy {
    /** Android needs to report sustained stillness before DailyBeat turns off the active service. */
    const val STILL_CONFIRMATION_MS = 5 * 60 * 1_000L

    /** Safety net if a handset never delivers a STILL activity-transition callback. */
    const val LOCATION_IDLE_TIMEOUT_MS = 8 * 60 * 1_000L

    /** A new location this far from the prior accepted fix resets the idle safety-net timer. */
    const val MEANINGFUL_MOVEMENT_M = 60.0

    fun shouldStopForStillness(
        stillSinceMs: Long,
        nowMs: Long,
        captureEnabled: Boolean,
    ): Boolean = captureEnabled && stillSinceMs > 0L && nowMs - stillSinceMs >= STILL_CONFIRMATION_MS

    fun shouldStopForLocationIdle(
        lastMeaningfulMovementMs: Long,
        nowMs: Long,
        hasMotionWatcher: Boolean,
    ): Boolean = hasMotionWatcher &&
        lastMeaningfulMovementMs > 0L &&
        nowMs - lastMeaningfulMovementMs >= LOCATION_IDLE_TIMEOUT_MS
}
