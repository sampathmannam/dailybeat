package com.dailybeat.app.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.MainActivity
import com.dailybeat.app.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class DailyReminderTest {
    @Test fun reminderDoesNotClaimThatAnUngeneratedReportExists() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        app.settingsRepository.setAutoEveningReport(false)
        DailyReminderReceiver().onReceive(app, Intent())
        val notification = shadowOf(app.getSystemService(NotificationManager::class.java))
            .getNotification(DailyReminderScheduler.NOTIFICATION_ID)
        assertNotNull(notification)
        assertEquals(app.getString(R.string.reminder_body_report),
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            .contains("report is ready", ignoreCase = true))
        val opened = shadowOf(notification.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, opened.component?.className)
        assertEquals(LocalDate.now().toString(), reminderReviewDate(opened))
    }

    @Test fun reminderRetainsItsDateWhenOpenedAfterMidnight() {
        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        assertEquals("2026-09-24", reminderReviewDate(reviewDayIntent(app, LocalDate.of(2026, 9, 24))))
    }

    @Test fun unrelatedOrMalformedIntentsDoNotNavigateToAnArbitraryRoute() {
        assertNull(reminderReviewDate(null))
        assertNull(reminderReviewDate(Intent().putExtra(REVIEW_DAY_EXTRA, "2026-09-24")))
        assertNull(reminderReviewDate(Intent(REVIEW_DAY_ACTION).putExtra(REVIEW_DAY_EXTRA, "../../settings")))
        assertNull(reminderReviewDate(Intent(REVIEW_DAY_ACTION).putExtra(REVIEW_DAY_EXTRA, "2026-02-31")))
        assertNull(reminderReviewDate(Intent(REVIEW_DAY_ACTION).putExtra(REVIEW_DAY_EXTRA, "+999999999-12-31")))
        assertNull(reminderReviewDate(Intent(REVIEW_DAY_ACTION).putExtra(REVIEW_DAY_EXTRA, 42)))
    }
}
