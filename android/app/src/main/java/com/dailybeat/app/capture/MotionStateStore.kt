package com.dailybeat.app.capture

import android.content.Context

/** Durable, coordinate-free state shared by the transition receiver and delayed stillness worker. */
class MotionStateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val state: MotionState
        get() = MotionState.entries.firstOrNull { it.name == prefs.getString(KEY_STATE, null) }
            ?: MotionState.UNKNOWN

    val changedAtMs: Long
        get() = prefs.getLong(KEY_CHANGED_AT_MS, 0L)

    val watcherArmed: Boolean
        get() = prefs.getBoolean(KEY_WATCHER_ARMED, false)

    fun record(state: MotionState, atMs: Long) {
        prefs.edit()
            .putString(KEY_STATE, state.name)
            .putLong(KEY_CHANGED_AT_MS, atMs)
            .apply()
    }

    fun setWatcherArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_WATCHER_ARMED, armed).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun clearSynchronously() {
        check(prefs.edit().clear().commit()) { "Unable to clear capture motion state." }
    }

    private companion object {
        const val PREFS = "capture_motion_state"
        const val KEY_STATE = "state"
        const val KEY_CHANGED_AT_MS = "changed_at_ms"
        const val KEY_WATCHER_ARMED = "watcher_armed"
    }
}

enum class MotionState { UNKNOWN, MOVING, STILL }
