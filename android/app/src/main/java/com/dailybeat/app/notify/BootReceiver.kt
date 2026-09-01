package com.dailybeat.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dailybeat.app.capture.CaptureController

/** Restore only an explicitly active patrol session after device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        CaptureController.applyFromSettings(context)
    }
}
