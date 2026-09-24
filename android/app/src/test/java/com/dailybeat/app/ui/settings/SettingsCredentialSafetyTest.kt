package com.dailybeat.app.ui.settings

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.backup.RestoreSettingsException
import com.dailybeat.app.cloud.CloudTextGenerator
import com.dailybeat.app.data.settings.AppSettings
import com.dailybeat.app.data.settings.InMemoryApiKeyStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class SettingsCredentialSafetyTest {
    private lateinit var app: DailyBeatApp
    private lateinit var keys: InMemoryApiKeyStore
    private lateinit var model: SettingsViewModel
    private val store = ViewModelStore()
    private var connection: suspend () -> Result<String> = { Result.success("connected") }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        keys = InMemoryApiKeyStore(app, null)
        app.settingsRepository.setOfficerName("Credential safety test")
        model = SettingsViewModel(app, keys, object : CloudTextGenerator {
            override suspend fun generate(settings: AppSettings, systemPrompt: String,
                userPrompt: String, maxOutputTokens: Int): Result<String> = connection()
        })
        store.put("settings", model)
        withTimeout(10_000) { model.uiState.first { it.officerName == "Credential safety test" } }
        Unit
    }

    @After fun cleanup() = runBlocking {
        val job = model.viewModelScope.coroutineContext[Job]
        store.clear()
        withTimeout(10_000) { job?.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    @Test fun `queued key save cannot recreate a credential after local erase`() = runBlocking {
        CaptureStorageGate.mutex.withLock {
            model.setApiKeyDraft("old-private-key")
            model.saveApiKey()
            assertTrue(model.uiState.value.apiKeyBusy)
            keys.clearApiKey()
            CaptureStorageGate.invalidatePersonalData()
        }
        withTimeout(10_000) { model.uiState.first { !it.apiKeyBusy && it.apiKeyDraft.isEmpty() } }
        assertNull(keys.getApiKey())
    }

    @Test fun `data replacement clears sensitive drafts and stale submissions`() = runBlocking {
        model.setApiKeyDraft("old-key")
        model.setBackupEmail("person@example.invalid")
        model.setBackupPassword("private-backup-password")
        model.setRecoveryPassphrase("private recovery passphrase")
        model.setRecoveryConfirmation("private recovery passphrase")
        model.setAccountDeletePassword("private-delete-password")
        CaptureStorageGate.mutex.withLock { CaptureStorageGate.invalidatePersonalData() }
        model.saveApiKey()
        model.signInToBackup()
        model.createBackupAccount()
        val state = model.uiState.value
        assertEquals("", state.apiKeyDraft)
        assertEquals("", state.backupEmailDraft)
        assertEquals("", state.backupPasswordDraft)
        assertEquals("", state.recoveryPassphrase)
        assertEquals("", state.recoveryConfirmation)
        assertEquals("", state.accountDeletePassword)
        assertFalse(state.backupBusy)
        assertNull(keys.getApiKey())
    }

    @Test fun `queued cloud configuration cannot overwrite restored settings`() = runBlocking {
        CaptureStorageGate.mutex.withLock {
            model.setCloudModel("old-model")
            app.settingsRepository.setCloudModel("restored-model")
            CaptureStorageGate.invalidatePersonalData()
        }
        withTimeout(10_000) { model.uiState.first { it.cloudModel == "restored-model" } }
        assertEquals("restored-model", app.settingsRepository.get().cloudModel)
    }

    @Test fun `connection request is cancelled and its late result is not displayed after replacement`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        connection = {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        model.testCloudConnection()
        withTimeout(10_000) { started.await() }
        CaptureStorageGate.mutex.withLock { CaptureStorageGate.invalidatePersonalData() }
        withTimeout(10_000) { cancelled.await() }
        assertFalse(model.uiState.value.cloudTesting)
        assertNull(model.uiState.value.cloudTestResult)
    }

    @Test fun `fresh credential save and confirmed removal still work`() = runBlocking {
        model.setApiKeyDraft("fresh-key")
        model.saveApiKey()
        withTimeout(10_000) { model.uiState.first { it.hasApiKey && !it.apiKeyBusy } }
        assertEquals("fresh-key", keys.getApiKey())
        model.requestApiKeyRemoval()
        model.confirmApiKeyRemoval()
        withTimeout(10_000) { model.uiState.first { !it.hasApiKey && !it.apiKeyBusy } }
        assertNull(keys.getApiKey())
    }

    @Test fun `old removal confirmation cannot delete a credential in replaced state`() = runBlocking {
        keys.setApiKey("original-key")
        model.refresh()
        withTimeout(10_000) { model.uiState.first { it.hasApiKey } }
        model.requestApiKeyRemoval()
        CaptureStorageGate.mutex.withLock {
            keys.setApiKey("replacement-key")
            CaptureStorageGate.invalidatePersonalData()
        }
        withTimeout(10_000) { model.uiState.first { it.hasApiKey } }
        model.confirmApiKeyRemoval()
        assertEquals("replacement-key", keys.getApiKey())
        assertFalse(model.uiState.value.apiKeyRemovalConfirmation)
    }

    @Test fun `cloud withdrawal is immediate and defeats an older queued enable`() = runBlocking {
        app.settingsRepository.setCloudLlmEnabled(true)
        CaptureStorageGate.mutex.withLock {
            model.setCloudLlmEnabled(true)
            model.setCloudLlmEnabled(false)
            assertFalse(app.settingsRepository.get().cloudLlmEnabled)
            assertFalse(model.uiState.value.cloudLlmEnabled)
        }
        CaptureStorageGate.mutex.withLock { assertFalse(app.settingsRepository.get().cloudLlmEnabled) }
    }

    @Test fun `blank lookup save withdraws immediately and defeats an older queued endpoint`() = runBlocking {
        app.settingsRepository.setGeocodingEndpoint("https://maps.example/reverse")
        CaptureStorageGate.mutex.withLock {
            model.setGeocodingDraft("https://maps.example/new-reverse")
            model.saveGeocodingEndpoint()
            model.setGeocodingDraft("")
            model.saveGeocodingEndpoint()
            assertEquals("", app.settingsRepository.geocodingEndpoint())
        }
        CaptureStorageGate.mutex.withLock { assertEquals("", app.settingsRepository.geocodingEndpoint()) }
    }

    @Test fun `automatic report withdrawal is immediate and defeats a queued enable`() = runBlocking {
        app.settingsRepository.setAutoEveningReport(true)
        CaptureStorageGate.mutex.withLock {
            model.setAutoEveningReport(true)
            model.setAutoEveningReport(false)
            assertFalse(app.settingsRepository.get().autoEveningReport)
        }
        CaptureStorageGate.mutex.withLock { assertFalse(app.settingsRepository.get().autoEveningReport) }
    }

    @Test fun `confirmed key removal denies cloud use before storage deletion can proceed`() = runBlocking {
        keys.setApiKey("old-key")
        app.settingsRepository.setCloudLlmEnabled(true)
        model.refresh()
        withTimeout(10_000) { model.uiState.first { it.hasApiKey } }
        CaptureStorageGate.mutex.withLock {
            model.setCloudLlmEnabled(true)
            model.requestApiKeyRemoval()
            model.confirmApiKeyRemoval()
            assertFalse(app.settingsRepository.get().cloudLlmEnabled)
            assertEquals("old-key", keys.getApiKey())
        }
        withTimeout(10_000) { model.uiState.first { !it.apiKeyBusy } }
        assertNull(keys.getApiKey())
        assertFalse(app.settingsRepository.get().cloudLlmEnabled)
    }

    @Test fun `empty backup form shows validation without requiring a previous edit`() {
        model.backupNow()
        assertTrue(model.uiState.value.backupMessageIsError)
        assertTrue(model.uiState.value.backupMessage.orEmpty().contains("recovery passphrase"))
        assertFalse(model.uiState.value.backupBusy)
    }

    @Test fun `empty restore form shows validation without a network operation`() {
        model.requestBackupRestore()
        model.confirmBackupRestore()
        assertTrue(model.uiState.value.backupMessageIsError)
        assertTrue(model.uiState.value.backupMessage.orEmpty().contains("recovery passphrase"))
        assertFalse(model.uiState.value.backupBusy)
    }

    @Test fun `older key save preserves a newer draft typed while waiting`() = runBlocking {
        CaptureStorageGate.mutex.withLock {
            model.setApiKeyDraft("first-key")
            model.saveApiKey()
            model.setApiKeyDraft("newer-draft")
        }
        withTimeout(10_000) { model.uiState.first { !it.apiKeyBusy } }
        assertEquals("first-key", keys.getApiKey())
        assertEquals("newer-draft", model.uiState.value.apiKeyDraft)
    }

    @Test fun `connection completion preserves a newer key draft`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        connection = { started.complete(Unit); finish.await(); Result.success("connected") }
        model.setApiKeyDraft("tested-key")
        model.testCloudConnection()
        withTimeout(10_000) { started.await() }
        model.setApiKeyDraft("newer-untested-draft")
        finish.complete(Unit)
        withTimeout(10_000) { model.uiState.first { !it.cloudTesting } }
        assertEquals("tested-key", keys.getApiKey())
        assertEquals("newer-untested-draft", model.uiState.value.apiKeyDraft)
    }

    @Test fun `cloud withdrawal cancels a running connection test`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        connection = { started.complete(Unit); try { awaitCancellation() } finally { cancelled.complete(Unit) } }
        model.testCloudConnection()
        withTimeout(10_000) { started.await() }
        model.setCloudLlmEnabled(false)
        withTimeout(10_000) { cancelled.await() }
        assertFalse(model.uiState.value.cloudTesting)
        assertNull(model.uiState.value.cloudTestResult)
    }

    @Test fun `own postcommit preference failure is shown as a partial restore`() = runBlocking {
        val startingGeneration = CaptureStorageGate.dataGeneration.get()
        CaptureStorageGate.mutex.withLock { CaptureStorageGate.invalidatePersonalData() }
        val error = RestoreSettingsException(startingGeneration + 1L, IllegalStateException("Preference write failed"))
        model.showBackupRestoreFailure(error, startingGeneration)
        assertTrue(model.uiState.value.backupMessageIsError)
        assertEquals(error.message, model.uiState.value.backupMessage)
        assertFalse(model.uiState.value.backupBusy)
    }

    @Test fun `postcommit failure from an older restore cannot replace newer status`() = runBlocking {
        val startingGeneration = CaptureStorageGate.dataGeneration.get()
        CaptureStorageGate.mutex.withLock {
            CaptureStorageGate.invalidatePersonalData()
            CaptureStorageGate.invalidatePersonalData()
        }
        model.backupNow()
        val currentMessage = model.uiState.value.backupMessage
        val error = RestoreSettingsException(startingGeneration + 1L, IllegalStateException("Preference write failed"))
        model.showBackupRestoreFailure(error, startingGeneration)
        assertEquals(currentMessage, model.uiState.value.backupMessage)
        model.showBackupRestoreFailure(IllegalStateException("Old request failed"), startingGeneration)
        assertEquals(currentMessage, model.uiState.value.backupMessage)
    }
}
