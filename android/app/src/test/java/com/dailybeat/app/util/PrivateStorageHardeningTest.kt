package com.dailybeat.app.util

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.retention.PrivateStorageMigration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrivateStorageHardeningTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private fun context(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val files = temporaryFolder.newFolder()
        val cache = temporaryFolder.newFolder()
        val primary = temporaryFolder.newFolder()
        val secondary = temporaryFolder.newFolder()
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = files
            override fun getCacheDir(): File = cache
            override fun getExternalFilesDirs(type: String?): Array<File> = arrayOf(primary, secondary)
            override fun getExternalFilesDir(type: String?): File = primary
        }
    }

    @Test fun exportsUsePrivateStorageEvenWhenExternalStorageIsAvailable() {
        val context = context()
        val file = AppStorage.outputFile(context, "synthetic.pdf")
        assertEquals(File(context.filesDir, "DailyBeat/exports").canonicalFile, file.parentFile)
        assertFalse(file.toPath().startsWith(context.getExternalFilesDir(null)!!.toPath()))
    }

    @Test fun migrationPreservesDocumentsFromEveryLegacyRootAndIsIdempotent() = runBlocking {
        val context = context()
        val legacy = AppStorage.legacyOutputDirs(context).mapIndexed { index, root ->
            root.mkdirs()
            File(root, "2026-09-21.pdf").apply { writeText("synthetic diary $index") }
        }
        File(legacy.first().parentFile, "capture_audit.log").writeText("synthetic audit\n")

        assertTrue(PrivateStorageMigration.migrate(context))

        val exports = AppStorage.outputDir(context).listFiles()!!.toList()
        assertEquals(setOf("synthetic diary 0", "synthetic diary 1", "synthetic diary 2"), exports.map { it.readText() }.toSet())
        assertTrue(legacy.none { it.exists() })
        assertEquals(listOf("synthetic audit"), CaptureAuditLog.readRecent(context))
        assertFalse(exports.any { it.name.endsWith(".log") })
        assertTrue(PrivateStorageMigration.migrate(context))
        assertEquals(exports.map { it.name }.toSet(), AppStorage.outputDir(context).listFiles()!!.map { it.name }.toSet())
    }

    @Test fun failedMigrationPreservesOriginalExport() {
        val context = context()
        val external = File(context.getExternalFilesDir(null), "DailyBeat").apply { mkdirs() }
        val original = File(external, "old.pdf").apply { writeText("private original") }
        File(context.filesDir, "DailyBeat").apply { mkdirs() }
        File(context.filesDir, "DailyBeat/exports").writeText("blocked directory")

        assertTrue(runCatching { AppStorage.migrateLegacyOutputs(context) }.isFailure)
        assertEquals("private original", original.readText())
    }

    @Test fun erasureClearsBothLegacyRootsNestedExportsAndInterruptedCacheFiles() {
        val context = context()
        val tracked = AppStorage.legacyOutputDirs(context).map { root ->
            File(root, "old.pdf").apply { parentFile!!.mkdirs(); writeText("private") }
        } + listOf(
            AppStorage.outputFile(context, "current.pdf").apply { writeText("private") },
            File(AppStorage.exportStagingDir(context), "interrupted.tmp").apply { writeText("private") },
            File(context.cacheDir, "dailybeat-2026-09-21-123456.pdf").apply { writeText("private") },
        )
        val unrelated = File(context.cacheDir, "unrelated-cache.bin").apply { writeText("keep") }

        assertTrue(AppStorage.clearGeneratedFiles(context))

        assertTrue(tracked.none { it.exists() })
        assertEquals("keep", unrelated.readText())
    }

    @Test fun migrationAndErasureDoNotFollowLegacySymlinks() {
        val context = context()
        val outside = temporaryFolder.newFile().apply { writeText("do not read or truncate") }
        val external = File(context.getExternalFilesDir(null), "DailyBeat").apply { mkdirs() }
        val link = File(external, "malicious.pdf")
        Files.createSymbolicLink(link.toPath(), outside.toPath())

        assertTrue(AppStorage.migrateLegacyOutputs(context))
        assertTrue(AppStorage.outputDir(context).listFiles()!!.isEmpty())
        assertFalse(Files.exists(link.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        assertTrue(AppStorage.clearGeneratedFiles(context))
        assertEquals("do not read or truncate", outside.readText())
    }

    @Test fun fileProviderSharesOnlyCompletedExports() {
        // Use the real app roots so FileProvider's manifest-derived strategy resolves them.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authority = "${context.packageName}.fileprovider"
        val exported = AppStorage.outputFile(context, "synthetic-provider.pdf")
        assertEquals("content", FileProvider.getUriForFile(context, authority, exported).scheme)
        val denied = listOf(
            File(context.filesDir, "diagnostics/capture_audit.log"),
            File(AppStorage.exportStagingDir(context), "partial.pdf"),
            File(context.filesDir, "DailyBeat/old.pdf"),
            File(context.getExternalFilesDir(null), "DailyBeat/old.pdf"),
            File(context.filesDir, "../databases/dailybeat.db"),
        )
        denied.forEach { file ->
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, authority, file)
            }
        }
    }
}
