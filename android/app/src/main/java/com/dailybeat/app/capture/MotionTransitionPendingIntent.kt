package com.dailybeat.app.capture

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Explicit, private destination; only the result extras need to be filled by the provider. */
internal object MotionTransitionPendingIntent {
    const val ACTION = "com.dailybeat.app.capture.MOTION_TRANSITION"
    private const val REQUEST_CODE = 4_106

    private fun intent(context: Context) =
        Intent(context, MotionTransitionReceiver::class.java).setAction(ACTION)

    private val mutableFlag: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

    fun create(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, intent(context), PendingIntent.FLAG_CANCEL_CURRENT or mutableFlag,
    )

    /** Includes the immutable token used by older releases; never creates a token to clean up. */
    fun existing(context: Context): List<PendingIntent> = listOfNotNull(
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent(context), PendingIntent.FLAG_NO_CREATE or mutableFlag,
        ),
        PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent(context),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).distinct()
}
