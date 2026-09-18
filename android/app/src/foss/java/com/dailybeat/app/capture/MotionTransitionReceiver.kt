package com.dailybeat.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Compatibility component for upgrades from a Play Services build; never consumes remote events. */
class MotionTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Unit
}
