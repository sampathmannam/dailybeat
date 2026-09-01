package com.dailybeat.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.ui.onboarding.OnboardingScreen
import com.dailybeat.app.ui.auth.PatrolGridLoadingScreen
import com.dailybeat.app.ui.auth.PatrolGridLoginScreen
import com.dailybeat.app.ui.patrol.PatrolGridAppScaffold
import com.dailybeat.app.ui.theme.DailyBeatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            CaptureController.applyFromSettings(this)
        } else {
            CaptureController.applyFromSettings(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hardenSensitiveWindow()
        val app = application as DailyBeatApp
        val showOnboarding = !app.settingsRepository.isOnboardingComplete()

        setContent {
            DailyBeatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (app.isPatrolGridConfigured) {
                        SecurePatrolGridEntry(
                            app = app,
                            onRequestLocationPermission = ::requestRuntimePermissions,
                        )
                    } else {
                        var onboardingDone by remember { mutableStateOf(!showOnboarding) }
                        if (!onboardingDone) {
                            OnboardingScreen(
                                onComplete = { officerName, role ->
                                    app.settingsRepository.setOfficerName(officerName)
                                    app.settingsRepository.setPatrolRole(role)
                                    app.settingsRepository.setOnboardingComplete(true)
                                    requestRuntimePermissions()
                                    onboardingDone = true
                                },
                            )
                        } else {
                            PatrolGridAppScaffold(onRequestLocationPermission = ::requestRuntimePermissions)
                        }
                    }
                }
            }
        }

    }

    private fun hardenSensitiveWindow() {
        if (!BuildConfig.DEBUG) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
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

}

private enum class SecureEntryState { CHECKING, SIGNED_OUT, SIGNING_IN, READY }

@androidx.compose.runtime.Composable
private fun SecurePatrolGridEntry(
    app: DailyBeatApp,
    onRequestLocationPermission: () -> Unit,
) {
    var state by remember { mutableStateOf(SecureEntryState.CHECKING) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun acceptIdentity(identity: com.dailybeat.app.patrolgrid.PatrolGridIdentity) {
        app.settingsRepository.setOfficerName(identity.displayName)
        app.settingsRepository.setPatrolRole(identity.role)
        app.settingsRepository.setOnboardingComplete(true)
        state = SecureEntryState.READY
    }

    LaunchedEffect(Unit) {
        if (app.patrolGridRemote.currentSession() == null) {
            state = SecureEntryState.SIGNED_OUT
        } else {
            app.patrolGridRemote.loadIdentity().fold(
                onSuccess = ::acceptIdentity,
                onFailure = { failure ->
                    val current = app.patrolGridRemote.currentSession()
                    val cachedIdentity = current?.userId?.let { userId ->
                        app.patrolGridSnapshotCache.load(userId)?.identity
                    }
                    if (cachedIdentity != null) {
                        acceptIdentity(cachedIdentity)
                    } else {
                        app.patrolGridRemote.signOut()
                        error = failure.message ?: "Secure access could not be verified."
                        state = SecureEntryState.SIGNED_OUT
                    }
                },
            )
        }
    }

    when (state) {
        SecureEntryState.CHECKING -> PatrolGridLoadingScreen()
        SecureEntryState.SIGNED_OUT,
        SecureEntryState.SIGNING_IN,
        -> PatrolGridLoginScreen(
            loading = state == SecureEntryState.SIGNING_IN,
            error = error,
            onSignIn = { email, password ->
                state = SecureEntryState.SIGNING_IN
                error = null
                scope.launch {
                    app.patrolGridRemote.signIn(email, password).fold(
                        onSuccess = {
                            acceptIdentity(it)
                        },
                        onFailure = {
                            error = it.message ?: "Sign-in failed."
                            state = SecureEntryState.SIGNED_OUT
                        },
                    )
                }
            },
        )
        SecureEntryState.READY -> PatrolGridAppScaffold(
            onSignedOut = { state = SecureEntryState.SIGNED_OUT },
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}
