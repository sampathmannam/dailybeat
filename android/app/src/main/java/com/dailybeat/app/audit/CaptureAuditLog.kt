package com.dailybeat.app.audit

import android.content.Context
import com.dailybeat.app.util.AppStorage
import com.dailybeat.app.util.InputPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.nio.file.Files

/**
 * Append-only capture audit trail for transparency and debugging.
 * Stored locally; never sent unless user exports or generates a cloud report.
 */
object CaptureAuditLog {

    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val MAX_RETAINED_LINES = 500
    private const val MAX_DETAIL_CHARS = 300
    private val fileLock = Any()

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    suspend fun log(context: Context, category: String, detail: String) = withContext(Dispatchers.IO) {
        val safeCategory = category.replace(Regex("[^A-Za-z0-9_-]"), "-").take(32)
        val safeDetail = InputPolicy.bounded(
            detail.replace(Regex("[\\r\\n\\u2028\\u2029]+"), " "),
            MAX_DETAIL_CHARS,
        )
        val line = "${timeFmt.format(Instant.now())} | $safeCategory | $safeDetail\n"
        try {
            synchronized(fileLock) {
                val file = auditFile(context)
                file.parentFile?.mkdirs()
                if (file.length() >= MAX_FILE_BYTES) {
                    val retained = newestLogLines(file, MAX_RETAINED_LINES / 2, MAX_FILE_BYTES.toInt())
                    file.writeText(retained.joinToString("\n", postfix = if (retained.isEmpty()) "" else "\n"))
                }
                file.appendText(line)
            }
        } catch (_: Exception) {
            // Audit logging must never crash capture paths.
        }
    }

    suspend fun readRecent(context: Context, maxLines: Int = 80): List<String> =
        withContext(Dispatchers.IO) {
            try {
                synchronized(fileLock) {
                    newestLogLines(auditFile(context), maxLines.coerceIn(0, MAX_RETAINED_LINES), MAX_FILE_BYTES.toInt())
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun clear(context: Context): Boolean = runCatching {
        synchronized(fileLock) {
            var cleared = AppStorage.clearSensitiveFileVerified(auditFile(context))
            AppStorage.legacyOutputDirs(context).forEach { directory ->
                val legacy = if (Files.isSymbolicLink(directory.toPath())) directory
                    else File(directory, "capture_audit.log")
                cleared = AppStorage.clearSensitiveFileVerified(legacy) && cleared
            }
            cleared
        }
    }.getOrDefault(false)

    /** Keep the bounded audit history, but take it outside every FileProvider path. */
    fun migrateLegacyStorage(context: Context): Boolean = synchronized(fileLock) {
        var succeeded = true
        val target = auditFile(context)
        AppStorage.legacyOutputDirs(context).forEach { directory ->
            if (Files.isSymbolicLink(directory.toPath())) {
                succeeded = AppStorage.clearSensitiveFileVerified(directory) && succeeded
                return@forEach
            }
            val legacy = File(directory, "capture_audit.log")
            if (Files.isSymbolicLink(legacy.toPath())) {
                succeeded = AppStorage.clearSensitiveFileVerified(legacy) && succeeded
            } else if (legacy.isFile) {
                val migrated = runCatching {
                    val oldLines = newestLogLines(legacy, MAX_RETAINED_LINES, MAX_FILE_BYTES.toInt())
                    val current = newestLogLines(target, MAX_RETAINED_LINES, MAX_FILE_BYTES.toInt())
                    val combined = (oldLines + current).distinct().takeLast(MAX_RETAINED_LINES)
                    check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
                    val temporary = File.createTempFile("capture-audit-", ".tmp", AppStorage.exportStagingDir(context))
                    try {
                        temporary.writeText(combined.joinToString("\n", postfix = if (combined.isEmpty()) "" else "\n"))
                        check(temporary.renameTo(target)) { "Unable to finish audit migration." }
                    } finally {
                        AppStorage.clearSensitiveFile(temporary)
                    }
                    check(AppStorage.clearSensitiveFileVerified(legacy)) { "Unable to clear legacy audit." }
                }.isSuccess
                succeeded = migrated && succeeded
            }
        }
        succeeded
    }

    private fun auditFile(context: Context): File =
        File(context.filesDir, "diagnostics/capture_audit.log")
}
