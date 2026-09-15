package com.dailybeat.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** Play Services transitions; route coordinates remain in [LocationService]. */
class MotionTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != MotionTransitionManager.ACTION_MOTION_TRANSITION ||
            !ActivityTransitionResult.hasResult(intent)
        ) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        result.transitionEvents
            .sortedBy { it.elapsedRealTimeNanos }
            .forEach { event ->
                if (event.transitionType != ActivityTransition.ACTIVITY_TRANSITION_ENTER) return@forEach
                val state = when (event.activityType) {
                    DetectedActivity.STILL -> MotionState.STILL
                    DetectedActivity.IN_VEHICLE,
                    DetectedActivity.ON_BICYCLE,
                    DetectedActivity.WALKING,
                    DetectedActivity.RUNNING,
                    -> MotionState.MOVING
                    else -> return@forEach
                }
                CaptureController.onMotionTransition(context, state)
            }
    }
}
