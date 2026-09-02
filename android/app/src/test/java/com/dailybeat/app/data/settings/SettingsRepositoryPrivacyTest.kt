package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryPrivacyTest {
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
    fun `privacy acknowledgement version persists across repository recreation`() {
        val repository = SettingsRepository(context)
        assertEquals(0, repository.acknowledgedPatrolGridPrivacyNoticeVersion())

        assertTrue(repository.acknowledgePatrolGridPrivacyNotice(1))

        assertEquals(
            1,
            SettingsRepository(context).acknowledgedPatrolGridPrivacyNoticeVersion(),
        )
    }

    @Test
    fun `invalid privacy acknowledgement version is not persisted`() {
        val repository = SettingsRepository(context)

        val rejected = runCatching {
            repository.acknowledgePatrolGridPrivacyNotice(0)
        }.isFailure

        assertTrue(rejected)
        assertFalse(preferences().contains("patrolgrid_privacy_notice_version"))
    }

    private fun preferences() =
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
}
