package com.dailybeat.app.capture

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/** Play Services implementation, excluded entirely from the FOSS build.
 * Lets Google Play services wake DailyBeat only when a person starts moving or becomes still.
 * No coordinates are retained here; it is solely a power-management trigger for capture.
 */
object MotionTransitionManager {
    private var registration: MotionWatcherRegistration<PendingIntent>? = null

    @SuppressLint("MissingPermission") // PermissionHelper verifies ACTIVITY_RECOGNITION immediately above.
    @Synchronized
    fun arm(context: Context) {
        registration(context).arm()
    }

    @SuppressLint("MissingPermission") // Removing a prior subscription is safe even after revocation.
    @Synchronized
    fun disarm(context: Context) {
        val appContext = context.applicationContext
        registration(appContext).disarm()
        removeExisting(appContext)
    }

    @SuppressLint("MissingPermission")
    private fun registration(context: Context): MotionWatcherRegistration<PendingIntent> {
        registration?.let { return it }
        val appContext = context.applicationContext
        val state = MotionStateStore(appContext)
        return MotionWatcherRegistration(
            isAllowed = {
                val app = appContext as? DailyBeatApp
                app != null && PermissionHelper.hasActivityRecognition(appContext) &&
                    PermissionHelper.canCaptureLocation(appContext) &&
                    app.settingsRepository.get().gpsCaptureEnabled &&
                    !app.settingsRepository.isCapturePaused()
            },
            setArmed = state::setWatcherArmed,
            createToken = {
                removeExisting(appContext)
                MotionTransitionPendingIntent.create(appContext)
            },
            register = { token, success, failure ->
                ActivityRecognition.getClient(appContext)
                    .requestActivityTransitionUpdates(request, token)
                    .addOnSuccessListener { success() }
                    .addOnFailureListener { failure(it) }
                Unit
            },
            remove = { token ->
                ActivityRecognition.getClient(appContext).removeActivityTransitionUpdates(token)
                Unit
            },
            cancel = PendingIntent::cancel,
            onFailure = { error ->
                OperationalFailureLog.record(
                    context = appContext,
                    category = "capture-motion-watcher",
                    retryable = true,
                    message = "Adaptive motion watcher could not start (${error.javaClass.simpleName}).",
                )
            },
        ).also { registration = it }
    }

    @SuppressLint("MissingPermission")
    private fun removeExisting(context: Context) {
        MotionTransitionPendingIntent.existing(context).forEach { token ->
            runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(token) }
            token.cancel()
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

    const val ACTION_MOTION_TRANSITION = MotionTransitionPendingIntent.ACTION
}
