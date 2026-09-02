package com.dailybeat.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dailybeat.app.capture.CaptureController
import com.dailybeat.app.backup.BackupRemoteException
import com.dailybeat.app.backup.isTransientBackupFailure
import com.dailybeat.app.patrolgrid.PatrolGridAccessDeniedException
import com.dailybeat.app.patrolgrid.PatrolGridRemoteException
import com.dailybeat.app.patrolgrid.PatrolTrackSyncWorker
import com.dailybeat.app.ui.onboarding.OnboardingScreen
import com.dailybeat.app.ui.auth.PatrolGridLoadingScreen
import com.dailybeat.app.ui.auth.PatrolGridLoginScreen
import com.dailybeat.app.ui.auth.PatrolGridConfigurationErrorScreen
import com.dailybeat.app.ui.auth.PatrolGridPrivacyGate
import com.dailybeat.app.ui.auth.isPatrolGridPrivacyPolicyConfigured
import com.dailybeat.app.ui.patrol.PatrolGridAppScaffold
import com.dailybeat.app.ui.theme.DailyBeatTheme
import kotlinx.coroutines.launch
import com.dailybeat.app.patrolgrid.isTransientPatrolGridFailure
import com.dailybeat.app.security.shouldLockPatrolGrid

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
        // Resume a still-active, explicitly started patrol after process death or an app update.
        if (!app.isPatrolGridConfigured) CaptureController.applyFromSettings(this)
        val showOnboarding = !app.settingsRepository.isOnboardingComplete()

        setContent {
            DailyBeatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (app.isPatrolGridConfigured) {
                        ConfiguredPatrolGridEntry(
                            app = app,
                            onRequestLocationPermission = ::requestRuntimePermissions,
                        )
                    } else if (BuildConfig.DEBUG) {
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
                    } else {
                        PatrolGridConfigurationErrorScreen()
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

@androidx.compose.runtime.Composable
private fun ConfiguredPatrolGridEntry(
    app: DailyBeatApp,
    onRequestLocationPermission: () -> Unit,
) {
    val retentionStartupState by app.patrolRetentionStartupState.collectAsState()
    when (retentionStartupState) {
        PatrolRetentionStartupState.CHECKING -> {
            PatrolGridLoadingScreen()
            return
        }
        PatrolRetentionStartupState.BLOCKED -> {
            PatrolRetentionStartupBlockedScreen(onRetry = app::retryPatrolRetentionStartupCheck)
            return
        }
        PatrolRetentionStartupState.RECOVERY_REQUIRED,
        PatrolRetentionStartupState.READY -> Unit
    }
    LaunchedEffect(Unit) { CaptureController.applyFromSettings(app) }

    val privacyConfigured = remember {
        isPatrolGridPrivacyPolicyConfigured(
            policyUrl = BuildConfig.PATROLGRID_PRIVACY_POLICY_URL,
            retentionDays = BuildConfig.PATROLGRID_RETENTION_DAYS,
        )
    }
    var acknowledgedNoticeVersion by remember {
        mutableIntStateOf(
            if (privacyConfigured) {
                app.settingsRepository.acknowledgedPatrolGridPrivacyNoticeVersion()
            } else {
                0
            },
        )
    }

    PatrolGridPrivacyGate(
        acknowledgedNoticeVersion = acknowledgedNoticeVersion,
        policyUrl = BuildConfig.PATROLGRID_PRIVACY_POLICY_URL,
        retentionDays = BuildConfig.PATROLGRID_RETENTION_DAYS,
        onAcknowledge = { version ->
            if (!privacyConfigured || app.settingsRepository.acknowledgePatrolGridPrivacyNotice(version)) {
                acknowledgedNoticeVersion = version
            }
        },
    ) {
        SecurePatrolGridEntry(
            app = app,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

@androidx.compose.runtime.Composable
private fun PatrolRetentionStartupBlockedScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(28.dp).testTag("retention_startup_blocked"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Secure cleanup must finish",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "PatrolGrid cannot show mission evidence or start GPS capture until the required local evidence cleanup succeeds. Report repeated failures immediately through your normal command, radio, or phone chain.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry, modifier = Modifier.testTag("retention_startup_retry")) {
                Text("Retry secure cleanup")
            }
        }
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
    var authenticatedUserId by remember { mutableStateOf<String?>(null) }
    var sessionGeneration by remember { mutableIntStateOf(0) }
    var locked by remember { mutableStateOf(app.settingsRepository.isPatrolGridLocked()) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun lockEntry() {
        if (app.patrolGridRemote.currentSession() == null) return
        app.settingsRepository.setPatrolGridLocked(true)
        locked = true
        error = null
        authenticatedUserId = null
        sessionGeneration += 1
        state = SecureEntryState.SIGNED_OUT
    }

    fun acceptIdentity(identity: com.dailybeat.app.patrolgrid.PatrolGridIdentity) {
        val pendingOwner = app.settingsRepository.get().patrolEvidenceOwnerId
        if (pendingOwner != null && pendingOwner != identity.userId) {
            app.patrolGridRemote.signOut()
            authenticatedUserId = null
            error = "This device has unsynchronized patrol evidence for the previous account. Sign in with that account to finish secure synchronization."
            state = SecureEntryState.SIGNED_OUT
            return
        }
        app.settingsRepository.setOfficerName(identity.displayName)
        app.settingsRepository.setPatrolRole(identity.role)
        app.settingsRepository.setOnboardingComplete(true)
        app.settingsRepository.setPatrolGridLocked(false)
        app.settingsRepository.setPatrolGridBackgroundedAtMs(null)
        locked = false
        authenticatedUserId = identity.userId
        if (app.settingsRepository.get().pendingPatrolCloseSessionId != null) {
            PatrolTrackSyncWorker.enqueue(app)
        }
        state = SecureEntryState.READY
    }

    suspend fun acceptAfterRetentionRecovery(
        identity: com.dailybeat.app.patrolgrid.PatrolGridIdentity,
    ) {
        if (app.patrolRetentionStartupState.value == PatrolRetentionStartupState.RECOVERY_REQUIRED) {
            val recovery = app.recoverPatrolRetentionClock()
            if (recovery.isFailure) {
                authenticatedUserId = null
                error = "Connect to PatrolGrid and sign in to recover the authoritative evidence-retention clock before mission evidence can be shown."
                state = SecureEntryState.SIGNED_OUT
                return
            }
        }
        acceptIdentity(identity)
    }

    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (state == SecureEntryState.READY) {
                    app.settingsRepository.setPatrolGridBackgroundedAtMs(System.currentTimeMillis())
                }
                Lifecycle.Event.ON_START -> if (
                    state == SecureEntryState.READY &&
                    shouldLockPatrolGrid(
                        hasSession = app.patrolGridRemote.currentSession() != null,
                        explicitlyLocked = app.settingsRepository.isPatrolGridLocked(),
                        backgroundedAtMs = app.settingsRepository.patrolGridBackgroundedAtMs(),
                        nowMs = System.currentTimeMillis(),
                    )
                ) {
                    lockEntry()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val currentSession = app.patrolGridRemote.currentSession()
        if (
            shouldLockPatrolGrid(
                hasSession = currentSession != null,
                explicitlyLocked = app.settingsRepository.isPatrolGridLocked(),
                backgroundedAtMs = app.settingsRepository.patrolGridBackgroundedAtMs(),
                nowMs = System.currentTimeMillis(),
            )
        ) {
            locked = true
            state = SecureEntryState.SIGNED_OUT
        } else if (currentSession == null) {
            state = SecureEntryState.SIGNED_OUT
        } else {
            app.patrolGridRemote.loadIdentity().fold(
                onSuccess = { acceptAfterRetentionRecovery(it) },
                onFailure = { failure ->
                    val current = app.patrolGridRemote.currentSession()
                    val cachedIdentity = current?.userId?.let { userId ->
                        app.patrolGridSnapshotCache.load(userId)?.identity
                    }
                    if (failure.isTransientPatrolGridFailure() && cachedIdentity != null) {
                        acceptAfterRetentionRecovery(cachedIdentity)
                    } else {
                        app.patrolGridRemote.signOut()
                        error = secureEntryError(failure, signingIn = false)
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
            locked = locked,
            onSignIn = { email, password ->
                state = SecureEntryState.SIGNING_IN
                error = null
                scope.launch {
                    app.patrolGridRemote.signIn(email, password).fold(
                        onSuccess = {
                            acceptAfterRetentionRecovery(it)
                        },
                        onFailure = {
                            error = secureEntryError(it, signingIn = true)
                            state = SecureEntryState.SIGNED_OUT
                        },
                    )
                }
            },
        )
        SecureEntryState.READY -> PatrolGridAppScaffold(
            viewModelKey = "secure:${authenticatedUserId.orEmpty()}:$sessionGeneration",
            onSignedOut = {
                app.settingsRepository.setPatrolGridLocked(false)
                app.settingsRepository.setPatrolGridBackgroundedAtMs(null)
                locked = false
                authenticatedUserId = null
                sessionGeneration += 1
                state = SecureEntryState.SIGNED_OUT
            },
            onSessionExpired = {
                app.settingsRepository.setPatrolGridLocked(false)
                app.settingsRepository.setPatrolGridBackgroundedAtMs(null)
                locked = false
                error = "Your secure session ended. Sign in again."
                authenticatedUserId = null
                sessionGeneration += 1
                state = SecureEntryState.SIGNED_OUT
            },
            onLocked = ::lockEntry,
            onRequestLocationPermission = onRequestLocationPermission,
        )
    }
}

private fun secureEntryError(error: Throwable, signingIn: Boolean): String = when {
    error.isTransientPatrolGridFailure() || error.isTransientBackupFailure() ->
        "PatrolGrid could not connect securely. Check the network and try again."
    error is PatrolGridAccessDeniedException -> error.message.orEmpty()
    error is PatrolGridRemoteException || error is BackupRemoteException ->
        error.message ?: if (signingIn) "Sign-in failed." else "Secure access could not be verified."
    else -> if (signingIn) {
        "Sign-in could not be completed securely. Try again."
    } else {
        "Secure access could not be verified. Sign in again."
    }
}
