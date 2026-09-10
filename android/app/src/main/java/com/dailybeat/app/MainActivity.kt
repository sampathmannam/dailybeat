package com.dailybeat.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dailybeat.app.data.settings.ThemePreference
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.ui.DailyBeatAppScaffold
import com.dailybeat.app.ui.onboarding.OnboardingScreen
import com.dailybeat.app.ui.theme.DailyBeatTheme
import com.dailybeat.app.util.PermissionHelper

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            requestBackgroundLocationIfNeeded()
        } else {
            CaptureController.applyFromSettings(this)
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        CaptureController.applyFromSettings(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DailyBeatApp
        val showOnboarding = !app.settingsRepository.isOnboardingComplete()

        setContent {
            val themePreference by app.settingsRepository.themePreference.collectAsStateWithLifecycle()
            val useDarkTheme = when (themePreference) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            DailyBeatTheme(darkTheme = useDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var onboardingDone by remember { mutableStateOf(!showOnboarding) }

                    if (!onboardingDone) {
                        OnboardingScreen(
                            onComplete = { officerName ->
                                app.settingsRepository.setOfficerName(officerName)
                                app.settingsRepository.setOnboardingComplete(true)
                                requestRuntimePermissions()
                                onboardingDone = true
                            },
                        )
                    } else {
                        DailyBeatAppScaffold()
                    }
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()
        // Passive capture does not survive a force-stop or an app update, and BootReceiver only
        // runs after a reboot. Re-apply it every time the officer opens the app so tracking is
        // never silently dead while the UI claims it is on.
        val app = application as DailyBeatApp
        if (app.settingsRepository.isOnboardingComplete()) {
            CaptureController.applyFromSettings(this)
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !PermissionHelper.hasBackgroundLocation(this)
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            CaptureController.applyFromSettings(this)
        }
    }
}
