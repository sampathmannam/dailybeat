package com.dailybeat.app.capture

import android.content.Context
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import com.dailybeat.app.util.PermissionHelper

object CaptureController {

    /** Called from a visible screen after the officer enables capture or opens DailyBeat. */
    fun applyFromSettings(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val settings = app.settingsRepository.get()

        val shouldStart = runCatching {
            settings.gpsCaptureEnabled &&
                !app.settingsRepository.isCapturePaused() &&
                PermissionHelper.canCaptureLocation(context)
        }.getOrElse { error ->
            OperationalFailureLog.record(
                context = context,
                category = "capture-permission",
                retryable = false,
                message = "Unable to evaluate location permission (${error.javaClass.simpleName}).",
            )
            false
        }
        val operation = if (shouldStart) {
            MotionTransitionManager.arm(context)
            val profile = if (MotionStateStore(context).state == MotionState.STILL) {
                ActiveCaptureProfile.SETTLING
            } else {
                ActiveCaptureProfile.MOVING
            }
            if (profile == ActiveCaptureProfile.SETTLING) {
                StillnessConfirmationWorker.schedule(context)
            }
            LocationService.start(context, profile)
        } else {
            runCatching {
                MotionTransitionManager.disarm(context)
                StillnessConfirmationWorker.cancel(context)
                LocationService.stop(context)
                Unit
            }
        }
        operation.onFailure { error ->
            OperationalFailureLog.record(
                context = context,
                category = "capture-gps",
                retryable = false,
                message = if (shouldStart) {
                    "GPS capture start failed (${error.javaClass.simpleName})."
                } else {
                    "GPS capture stop failed (${error.javaClass.simpleName})."
                },
            )
        }
    }

    /**
     * Used by boot and delayed-work callbacks. Re-arm the cheap motion watcher but do not create
     * a location foreground service from the background. Android will wake the app on movement;
     * only an "always" location grant is allowed to start the service from that callback.
     */
    fun rearmFromBackground(context: Context) {
        val app = context.applicationContext as DailyBeatApp
        val shouldWatch = runCatching {
            val settings = app.settingsRepository.get()
            settings.gpsCaptureEnabled &&
                !app.settingsRepository.isCapturePaused() &&
                PermissionHelper.canCaptureLocation(context)
        }.getOrElse { false }
        if (shouldWatch) {
            MotionTransitionManager.arm(context)
        } else {
            MotionTransitionManager.disarm(context)
            StillnessConfirmationWorker.cancel(context)
            LocationService.stop(context)
        }
    }

    /** Receives coarse movement transitions; no coordinates are handled on this path. */
    fun onMotionTransition(context: Context, state: MotionState) {
        val app = context.applicationContext as DailyBeatApp
        val now = System.currentTimeMillis()
        MotionStateStore(context).record(state, now)
        val settings = app.settingsRepository.get()
        val captureEnabled = settings.gpsCaptureEnabled &&
            !app.settingsRepository.isCapturePaused() &&
            PermissionHelper.canCaptureLocation(context)
        if (!captureEnabled) return

        when (state) {
            MotionState.MOVING -> {
                StillnessConfirmationWorker.cancel(context)
                if (LocationService.isRunning) {
                    LocationService.updateProfile(context, ActiveCaptureProfile.MOVING)
                } else if (PermissionHelper.canStartLocationCaptureFromBackground(context)) {
                    LocationService.start(context, ActiveCaptureProfile.MOVING)
                }
            }
            MotionState.STILL -> {
                if (LocationService.isRunning) {
                    LocationService.updateProfile(context, ActiveCaptureProfile.SETTLING)
                }
                StillnessConfirmationWorker.schedule(context)
            }
            MotionState.UNKNOWN -> Unit
        }
    }
}
