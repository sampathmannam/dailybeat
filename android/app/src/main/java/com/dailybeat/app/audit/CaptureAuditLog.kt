package com.dailybeat.app.audit

import android.content.Context
import com.dailybeat.app.util.AppStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque

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
        val safeDetail = detail.replace(Regex("[\\r\\n\\u2028\\u2029]+"), " ").take(MAX_DETAIL_CHARS)
        val line = "${timeFmt.format(Instant.now())} | $safeCategory | $safeDetail\n"
        try {
            synchronized(fileLock) {
                val file = auditFile(context)
                file.parentFile?.mkdirs()
                if (file.length() >= MAX_FILE_BYTES) {
                    val retained = newestLines(file, MAX_RETAINED_LINES / 2)
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
                    newestLines(auditFile(context), maxLines.coerceIn(0, MAX_RETAINED_LINES))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    fun clear(context: Context) {
        runCatching {
            synchronized(fileLock) { auditFile(context).delete() }
        }
    }

    private fun newestLines(file: File, limit: Int): List<String> {
        if (limit == 0 || !file.isFile) return emptyList()
        val lines = ArrayDeque<String>(limit)
        file.useLines { sequence ->
            sequence.forEach { line ->
                if (lines.size == limit) lines.removeFirst()
                lines.addLast(line)
            }
        }
        return lines.toList()
    }

    private fun auditFile(context: Context): File =
        AppStorage.outputFile(context, "capture_audit.log")
}
