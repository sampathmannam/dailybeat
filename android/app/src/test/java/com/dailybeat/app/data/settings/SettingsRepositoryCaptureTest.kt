package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryCaptureTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences().edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences().edit().clear().commit()
    }

    @Test
    fun `capture failure persists across process repository recreation and clears`() {
        val repository = SettingsRepository(context)
        repository.setPatrolCaptureError("Secure GPS recording stopped")

        assertEquals(
            "Secure GPS recording stopped",
            SettingsRepository(context).get().patrolCaptureError,
        )

        repository.setPatrolCaptureError(null)
        assertNull(SettingsRepository(context).get().patrolCaptureError)
    }

    private fun preferences() =
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
}
