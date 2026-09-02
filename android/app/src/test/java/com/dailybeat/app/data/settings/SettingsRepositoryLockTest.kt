package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryLockTest {
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
    fun `explicit lock and background timestamp persist and clear`() {
        val repository = SettingsRepository(context)
        assertFalse(repository.isPatrolGridLocked())
        assertNull(repository.patrolGridBackgroundedAtMs())

        repository.setPatrolGridLocked(true)
        repository.setPatrolGridBackgroundedAtMs(123_456L)

        val restored = SettingsRepository(context)
        assertTrue(restored.isPatrolGridLocked())
        assertEquals(123_456L, restored.patrolGridBackgroundedAtMs())

        restored.setPatrolGridLocked(false)
        restored.setPatrolGridBackgroundedAtMs(null)
        assertFalse(restored.isPatrolGridLocked())
        assertNull(restored.patrolGridBackgroundedAtMs())
    }

    private fun preferences() =
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
}
