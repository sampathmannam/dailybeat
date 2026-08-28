package com.dailybeat.app.audit

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Append-only capture audit trail for transparency and debugging.
 * Stored locally; never sent unless user exports or generates a cloud report.
 */
object CaptureAuditLog {

    private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun log(context: Context, category: String, detail: String) {
        val line = "${timeFmt.format(Instant.now())} | $category | ${detail.replace("\n", " ")}\n"
        try {
            val file = auditFile(context)
            file.parentFile?.mkdirs()
            file.appendText(line)
        } catch (_: Exception) {
            // Audit logging must never crash capture paths.
        }
    }

    fun readRecent(context: Context, maxLines: Int = 80): List<String> {
        val file = auditFile(context)
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(maxLines)
    }

    fun clear(context: Context) {
        auditFile(context).delete()
    }

    private fun auditFile(context: Context): File =
        File(context.getExternalFilesDir(null), "DailyBeat/capture_audit.log")
}
