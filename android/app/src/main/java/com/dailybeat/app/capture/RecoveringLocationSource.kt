package com.dailybeat.app.capture

import android.location.Location
import java.util.concurrent.TimeoutException

interface LocationSource {
    fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
              onReady: () -> Unit, onError: (Exception) -> Unit)
    fun stop()
    suspend fun current(): Location?
}

/** Main-thread subscriptions with a bounded startup and stale-callback protection. */
class RecoveringLocationSource(
    private val primary: LocationSource,
    private val fallback: LocationSource,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onFallback: (Exception) -> Unit = {},
) : LocationSource {
    private var generation = 0
    private var active: LocationSource? = null
    private var cancelDeadline: (() -> Unit)? = null

    override fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
                       onReady: () -> Unit, onError: (Exception) -> Unit) {
        stop()
        val epoch = generation
        active = primary
        fun isCurrent(source: LocationSource) = generation == epoch && active === source
        fun useFallback(error: Exception) {
            if (!isCurrent(primary)) return
            cancelDeadline?.invoke()
            cancelDeadline = null
            active = fallback
            runCatching { primary.stop() }
            runCatching { onFallback(error) }
            try {
                fallback.start(profile,
                    { if (isCurrent(fallback)) onLocations(it) },
                    { if (isCurrent(fallback)) onReady() },
                    { if (isCurrent(fallback)) onError(it) })
            } catch (failure: Exception) { if (isCurrent(fallback)) onError(failure) }
        }
        cancelDeadline = schedule(5_000L) { useFallback(TimeoutException("Location subscription timed out.")) }
        try {
            primary.start(profile,
                { if (isCurrent(primary)) onLocations(it) },
                {
                    if (isCurrent(primary)) {
                        cancelDeadline?.invoke()
                        cancelDeadline = null
                        onReady()
                    }
                }, ::useFallback)
        } catch (error: Exception) { useFallback(error) }
    }

    override fun stop() {
        generation++
        active = null
        cancelDeadline?.invoke()
        cancelDeadline = null
        runCatching { primary.stop() }
        runCatching { fallback.stop() }
    }

    override suspend fun current(): Location? = primary.current() ?: fallback.current()
}
