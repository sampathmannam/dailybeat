package com.dailybeat.app.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class SettingsViewModelCaptureTest {

    private lateinit var app: DailyBeatApp

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.settingsRepository.setCallLogEnabled(false)
    }

    @Test
    fun callLogSettingChangesAlwaysRouteUpdatedStateThroughCaptureController() {
        val appliedStates = mutableListOf<Boolean>()
        val viewModel = SettingsViewModel(app) { context: Context ->
            val dailyBeatApp = context.applicationContext as DailyBeatApp
            appliedStates += dailyBeatApp.settingsRepository.get().callLogEnabled
        }

        viewModel.setCallLogEnabled(true)
        viewModel.setCallLogEnabled(false)

        assertEquals(listOf(true, false), appliedStates)
        assertFalse(app.settingsRepository.get().callLogEnabled)
        assertFalse(viewModel.uiState.value.callLogEnabled)
    }
}
