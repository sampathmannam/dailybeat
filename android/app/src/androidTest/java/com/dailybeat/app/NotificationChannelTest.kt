package com.dailybeat.app

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dailybeat.app.capture.LocationService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelTest {
    @Test
    fun passiveCaptureStatusDoesNotCreateAnUnreadLauncherBadge() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val channel = requireNotNull(
            app.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(LocationService.CHANNEL_ID),
        )

        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.canShowBadge())
    }
}
