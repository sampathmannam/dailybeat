package com.dailybeat.app.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Lets Google Play services wake DailyBeat only when a person starts moving or becomes still.
 * No coordinates are retained here; it is solely a power-management trigger for capture.
 */
object MotionTransitionManager {

    @SuppressLint("MissingPermission") // PermissionHelper verifies ACTIVITY_RECOGNITION immediately above.
    fun arm(context: Context) {
        val appContext = context.applicationContext
        val state = MotionStateStore(appContext)
        if (!PermissionHelper.hasActivityRecognition(appContext)) {
            state.setWatcherArmed(false)
            return
        }
        state.setWatcherArmed(false)
        runCatching {
            ActivityRecognition.getClient(appContext)
                .requestActivityTransitionUpdates(request, pendingIntent(appContext))
                .addOnSuccessListener { state.setWatcherArmed(true) }
                .addOnFailureListener { error ->
                    OperationalFailureLog.record(
                        context = appContext,
                        category = "capture-motion-watcher",
                        retryable = true,
                        message = "Adaptive motion watcher could not start (${error.javaClass.simpleName}).",
                    )
                }
        }.onFailure { error ->
            OperationalFailureLog.record(
                context = appContext,
                category = "capture-motion-watcher",
                retryable = true,
                message = "Adaptive motion watcher could not start (${error.javaClass.simpleName}).",
            )
        }
    }

    @SuppressLint("MissingPermission") // Removing a prior subscription is safe even after revocation.
    fun disarm(context: Context) {
        val appContext = context.applicationContext
        MotionStateStore(appContext).setWatcherArmed(false)
        runCatching {
            ActivityRecognition.getClient(appContext)
                .removeActivityTransitionUpdates(pendingIntent(appContext))
        }
    }

    private val request: ActivityTransitionRequest by lazy {
        val movingTypes = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
        )
        val transitions = buildList {
            movingTypes.forEach { type ->
                add(transition(type, ActivityTransition.ACTIVITY_TRANSITION_ENTER))
            }
            add(transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_ENTER))
        }
        ActivityTransitionRequest(transitions)
    }

    private fun transition(type: Int, event: Int): ActivityTransition =
        ActivityTransition.Builder()
            .setActivityType(type)
            .setActivityTransition(event)
            .build()

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, MotionTransitionReceiver::class.java).setAction(ACTION_MOTION_TRANSITION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    const val ACTION_MOTION_TRANSITION = "com.dailybeat.app.capture.MOTION_TRANSITION"
    private const val REQUEST_CODE = 4_106
}
