package com.dailybeat.app.capture

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class MotionTransitionPendingIntentTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `provider can attach event extras but cannot replace explicit receiver or action`() {
        val token = MotionTransitionPendingIntent.create(context)
        val providerResult = Intent("untrusted.action")
            .setComponent(ComponentName("other.package", "OtherReceiver"))
            .putExtra("transition-result", byteArrayOf(1, 2, 3))

        token.send(context, 0, providerResult)

        val delivered = shadowOf(context).broadcastIntents.last()
        assertArrayEquals(byteArrayOf(1, 2, 3), delivered.getByteArrayExtra("transition-result"))
        assertEquals(MotionTransitionPendingIntent.ACTION, delivered.action)
        assertEquals(ComponentName(context, MotionTransitionReceiver::class.java), delivered.component)
        assertFalse(shadowOf(token).isImmutable)
    }

    @Test fun `replacement token remains valid if stale completion cancels old token`() {
        val old = MotionTransitionPendingIntent.create(context)
        val replacement = MotionTransitionPendingIntent.create(context)
        assertTrue(shadowOf(old).isCanceled)
        old.cancel()
        assertFalse(shadowOf(replacement).isCanceled)
        replacement.send(context, 0, Intent().putExtra("transition-result", "new"))
        assertEquals("new", shadowOf(context).broadcastIntents.last().getStringExtra("transition-result"))
    }

    @Test fun `upgrade cleanup finds immutable legacy token without creating registrations`() {
        assertTrue(MotionTransitionPendingIntent.existing(context).isEmpty())
        val legacy = PendingIntent.getBroadcast(
            context, 4_106,
            Intent(context, MotionTransitionReceiver::class.java).setAction(MotionTransitionPendingIntent.ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        legacy.send(context, 0, Intent().putExtra("transition-result", "dropped"))
        assertNull(shadowOf(context).broadcastIntents.last().getStringExtra("transition-result"))
        assertTrue(MotionTransitionPendingIntent.existing(context).contains(legacy))
        MotionTransitionPendingIntent.existing(context).forEach(PendingIntent::cancel)
        assertTrue(shadowOf(legacy).isCanceled)
        assertTrue(MotionTransitionPendingIntent.existing(context).isEmpty())
    }
}
