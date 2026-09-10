package com.dailybeat.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePreferenceTest {

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
    fun `theme preference changes immediately and survives a repository restart`() {
        val repository = SettingsRepository(context)

        assertEquals(ThemePreference.SYSTEM, repository.themePreference.value)

        repository.setThemePreference(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, repository.themePreference.value)
        assertEquals(ThemePreference.DARK, repository.get().themePreference)
        assertEquals(ThemePreference.DARK, SettingsRepository(context).themePreference.value)
    }

    @Test
    fun `unknown stored theme safely follows the system`() {
        preferences().edit().putString("theme_preference", "future-theme").commit()

        val repository = SettingsRepository(context)

        assertEquals(ThemePreference.SYSTEM, repository.themePreference.value)
        assertEquals(ThemePreference.SYSTEM, repository.get().themePreference)
    }

    private fun preferences() =
        context.getSharedPreferences("dailybeat_settings", Context.MODE_PRIVATE)
}
