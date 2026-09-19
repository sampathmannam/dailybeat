package com.dailybeat.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailybeat.app.backup.BackupConfiguration
import com.dailybeat.app.backup.BackupCoordinator
import com.dailybeat.app.backup.BackupRemote
import com.dailybeat.app.backup.ArchiveBackupRemote
import com.dailybeat.app.backup.PagedSnapshotStore
import com.dailybeat.app.backup.BackupSnapshot
import com.dailybeat.app.backup.EncryptedBackupSessionStore
import com.dailybeat.app.backup.LocalBackupStore
import com.dailybeat.app.backup.SnapshotStore
import com.dailybeat.app.backup.SupabaseBackupClient
import com.dailybeat.app.capture.LocationService
import com.dailybeat.app.data.model.DiaryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class CloudBackupLiveTest {

    @Test
    fun phoneBackupAndRestoreRoundTrip() = runBlocking {
        requireDisposableTestApp()
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("backupEmail").orEmpty()
        val password = arguments.getString("backupPassword").orEmpty()
        val required = arguments.getString("requireLiveBackup") == "true"
        val hasCredentials = email.isNotBlank() && password.isNotBlank()
        if (required) check(hasCredentials) { "Required live backup credentials were not provided." }
        assumeTrue("Optional live backup credentials were not provided.", hasCredentials)
        assertEquals(arguments.getString("backupEmailSha"), sha256(email))
        assertEquals(arguments.getString("backupPasswordSha"), sha256(password))
        assertEquals(
            arguments.getString("backupConfigSha"),
            sha256("${BuildConfig.SUPABASE_URL}|${BuildConfig.SUPABASE_ANON_KEY}"),
        )

        val app = ApplicationProvider.getApplicationContext<DailyBeatApp>()
        val configuration = BackupConfiguration(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        // Once credentials were deliberately supplied, a missing build configuration is a failure,
        // never a skipped test. A fresh random fixture secret needs no saved recovery credential.
        check(configuration.isConfigured) { "The requested live backup build has no Supabase configuration." }
        val randomBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val recovery = Base64.getEncoder().encodeToString(randomBytes).toCharArray()
        randomBytes.fill(0)
        val client = SupabaseBackupClient(configuration, EncryptedBackupSessionStore(app))
        var uploadedFixture: String? = null
        val trackedRemote = object : BackupRemote by client {
            override suspend fun upload(snapshotJson: String): Result<Unit> {
                // Retain the attempted envelope even if the server accepts it but its response is lost.
                uploadedFixture = snapshotJson
                return client.upload(snapshotJson)
            }
        }
        val local = LocalBackupStore(app.db, app.settingsRepository)
        val fixtureOnlyStore = object : SnapshotStore by local {
            override suspend fun createSnapshot(): BackupSnapshot = local.createSnapshot().also { snapshot ->
                check(snapshot.events.map { it.rawText } == listOf("Live cloud backup round trip") &&
                    snapshot.diaries.map { it.text } == listOf("Live cloud backup diary") &&
                    snapshot.places.isEmpty() && snapshot.visits.isEmpty() &&
                    snapshot.breadcrumbs.isEmpty() && snapshot.beatReviews.isEmpty()) {
                    "Unexpected non-fixture records appeared; nothing was uploaded."
                }
            }
        }
        val coordinator = BackupCoordinator(fixtureOnlyStore, trackedRemote)

        try {
            coordinator.signIn(email, password).getOrThrow()
            val original = client.download().getOrThrow()
            try {
                // Do not accidentally include physical-device fixes alongside the synthetic fixture.
                app.settingsRepository.setGpsEnabled(false)
                app.settingsRepository.setOfficerName("")
                app.settingsRepository.setSupervisorName("")
                LocationService.stop(app)
                withTimeout(10_000) { while (LocationService.isRunning) delay(100) }
                withContext(Dispatchers.IO) {
                    app.db.clearAllTables()
                    app.eventRepository.addManualEvent("Live cloud backup round trip")
                    app.db.diaries().upsert(
                        DiaryEntry("2026-08-31", "Live cloud backup diary", System.currentTimeMillis()),
                    )
                }
                coordinator.backupNow(recovery.copyOf()).getOrThrow()

                withContext(Dispatchers.IO) { app.db.clearAllTables() }
                assertEquals(0, withContext(Dispatchers.IO) { app.db.events().all().size })

                assertTrue(
                    "Wrong-passphrase recovery must fail",
                    coordinator.restoreNow("a different isolated QA recovery secret".toCharArray()).isFailure,
                )
                assertEquals(0, withContext(Dispatchers.IO) { app.db.events().all().size })

                // Reauthenticate and construct fresh client/coordinator instances before recovery.
                // Retain the existing session until sign-in succeeds so a failed sign-in does not
                // remove the credentials needed to restore the original QA backup in finally.
                val replacement = BackupCoordinator(
                    LocalBackupStore(app.db, app.settingsRepository),
                    SupabaseBackupClient(configuration, EncryptedBackupSessionStore(app)),
                )
                replacement.signIn(email, password).getOrThrow()
                replacement.restoreNow(recovery.copyOf()).getOrThrow()
                val restored = withContext(Dispatchers.IO) { app.db.events().all() }
                assertEquals(listOf("Live cloud backup round trip"), restored.map { it.rawText })
                assertEquals(
                    "Live cloud backup diary",
                    withContext(Dispatchers.IO) { app.db.diaries().forDate("2026-08-31")?.text },
                )
            } finally {
                if (uploadedFixture != null) {
                    val current = client.download().getOrThrow()
                    check(sameSnapshot(current?.snapshotJson, original?.snapshotJson) ||
                        sameSnapshot(current?.snapshotJson, uploadedFixture)) {
                        "The dedicated QA backup changed outside this test; cleanup stopped without overwriting it."
                    }
                    if (!sameSnapshot(current?.snapshotJson, original?.snapshotJson)) {
                        if (original != null) client.upload(original.snapshotJson).getOrThrow()
                        else deleteOwnQaFixture(configuration, client)
                    }
                    check(sameSnapshot(client.download().getOrThrow()?.snapshotJson, original?.snapshotJson)) {
                        "Dedicated QA backup cleanup verification failed."
                    }
                }
            }
            // Exercise the same archive-capable coordinator used by production. A nonempty
            // archive store is deliberately rejected so this test can never prune other data.
            check(client.versions().isEmpty()) { "The dedicated QA archive store must be empty before this test." }
            var archiveId: String? = null
            val archiveRemote = object : ArchiveBackupRemote by client {
                override suspend fun beginVersion(id: String) {
                    check(archiveId == null)
                    archiveId = id
                    client.beginVersion(id)
                }
            }
            val pagedFixture = object : PagedSnapshotStore by local {
                override suspend fun forEachPage(emit: suspend (BackupSnapshot) -> Unit) {
                    fixtureOnlyStore.createSnapshot() // Assert fixture-only local data before upload.
                    local.forEachPage(emit)
                }
            }
            try {
                val archiveCoordinator = BackupCoordinator(pagedFixture, archiveRemote, app.cacheDir)
                archiveCoordinator.backupNow(recovery.copyOf()).getOrThrow()
                val versions = archiveCoordinator.versions().getOrThrow()
                assertEquals(listOf(archiveId), versions.map { it.id })
                withContext(Dispatchers.IO) { app.db.clearAllTables() }
                val replacement = BackupCoordinator(
                    LocalBackupStore(app.db, app.settingsRepository),
                    SupabaseBackupClient(configuration, EncryptedBackupSessionStore(app)),
                    app.cacheDir,
                )
                replacement.signIn(email, password).getOrThrow()
                assertTrue(replacement.restoreNow("wrong isolated archive recovery passphrase".toCharArray(), archiveId).isFailure)
                assertEquals(0, withContext(Dispatchers.IO) { app.db.events().all().size })
                replacement.restoreNow(recovery.copyOf(), archiveId).getOrThrow()
                assertEquals(listOf("Live cloud backup round trip"),
                    withContext(Dispatchers.IO) { app.db.events().all().map { it.rawText } })
                assertEquals("Live cloud backup diary",
                    withContext(Dispatchers.IO) { app.db.diaries().forDate("2026-08-31")?.text })
            } finally {
                archiveId?.let { deleteOwnQaArchiveFixture(configuration, client, it) }
                check(client.versions().isEmpty()) { "Dedicated QA archive cleanup verification failed." }
                check(sameSnapshot(client.download().getOrThrow()?.snapshotJson, original?.snapshotJson)) {
                    "Archive test changed the legacy encrypted QA backup."
                }
            }
        } finally {
            recovery.fill('\u0000')
            coordinator.signOut()
        }
    }

    /** Delete only the UUID generated by this invocation, including a committed fixture. */
    private suspend fun deleteOwnQaArchiveFixture(configuration: BackupConfiguration, client: SupabaseBackupClient, id: String) =
        withContext(Dispatchers.IO) {
            requireDisposableTestApp()
            require(java.util.UUID.fromString(id).toString() == id)
            val session = checkNotNull(client.currentSession())
            val url = (configuration.baseUrl + "/rest/v1/dailybeat_backup_versions").toHttpUrl()
                .newBuilder().addQueryParameter("user_id", "eq.${session.userId}")
                .addQueryParameter("id", "eq.$id").build()
            val request = Request.Builder().url(url).header("apikey", configuration.anonymousKey)
                .header("Authorization", "Bearer ${session.accessToken}").delete().build()
            OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build().newCall(request).execute().use {
                    check(it.isSuccessful) { "Dedicated QA archive cleanup failed (${it.code})." }
                }
        }

    /** Test-only cleanup of this signed-in QA account. Never touches the legacy table. */
    private suspend fun deleteOwnQaFixture(configuration: BackupConfiguration, client: SupabaseBackupClient) =
        withContext(Dispatchers.IO) {
            requireDisposableTestApp()
            val session = checkNotNull(client.currentSession())
            val url = (configuration.baseUrl + "/rest/v1/dailybeat_encrypted_backups").toHttpUrl()
                .newBuilder().addQueryParameter("user_id", "eq.${session.userId}").build()
            val request = Request.Builder().url(url)
                .header("apikey", configuration.anonymousKey)
                .header("Authorization", "Bearer ${session.accessToken}").delete().build()
            OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
                .newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Dedicated QA fixture cleanup failed (${response.code})." }
                }
        }

    private fun sameSnapshot(left: String?, right: String?): Boolean = when {
        left == null || right == null -> left == right
        else -> jsonValue(JSONObject(left)) == jsonValue(JSONObject(right))
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
