package com.dailybeat.app.notify

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class BootReceiverTest {

    @Test
    fun bootCompletedDoesNotCrash() {
        val context = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
