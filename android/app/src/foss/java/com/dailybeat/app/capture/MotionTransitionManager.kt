package com.dailybeat.app.capture

import android.content.Context

/**
 * Android has no universally available, process-independent activity-transition equivalent.
 * Retain the continuous location profile instead of stopping capture on an unarmed watcher.
 */
object MotionTransitionManager {
    fun arm(context: Context) {
        MotionStateStore(context).apply {
            setWatcherArmed(false)
            record(MotionState.UNKNOWN, System.currentTimeMillis())
        }
    }
    fun disarm(context: Context) = arm(context)
}
