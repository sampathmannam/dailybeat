package com.dailybeat.app.audit

import android.content.Context
import com.dailybeat.app.BuildConfig
import java.io.File
import java.time.Instant
import java.util.ArrayDeque
import java.util.Locale

object OperationalFailureLog {

    private const val MAX_LINES = 80
    private const val MAX_MESSAGE_LENGTH = 160

    private val bearerToken = Regex("(?i)\\bbearer\\s+\\S+")
    private val secretKeyToken = Regex("(?i)\\bsk-[a-z0-9._-]+")
    private val apiKey = Regex("(?i)\\bapi[\\s_-]*key(?:\\s*[:=]\\s*|\\s+)\\S+")
    private val sensitivePayload = Regex(
        "(?i)\\b(prompt|diary)\\s*=\\s*.*?(?=\\s+" +
            "(?:prompt|diary|lat(?:itude)?|lon(?:gitude)?|lng|api[\\s_-]*key)\\s*[:=]|$)",
    )
    private val labeledCoordinatePair = Regex(
        "(?i)\\b(?:lat|latitude)\\s*[:=]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?\\s*[,;/ ]+\\s*" +
            "(?:lon|lng|longitude)\\s*[:=]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?",
    )
    private val reversedLabeledCoordinatePair = Regex(
        "(?i)\\b(?:lon|lng|longitude)\\s*[:=]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?\\s*[,;/ ]+\\s*" +
            "(?:lat|latitude)\\s*[:=]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?",
    )
    private val labeledCoordinate = Regex(
        "(?i)\\b(?:lat|latitude|lon|lng|longitude)\\s*[:=]\\s*[+-]?\\d{1,3}(?:\\.\\d+)?",
    )
    private val bareCoordinatePair = Regex(
        "(?<![\\d.])[+-]?(?:90(?:\\.0+)?|[0-8]?\\d(?:\\.\\d+)?)\\s*[,/]\\s*" +
            "[+-]?(?:180(?:\\.0+)?|1[0-7]\\d(?:\\.\\d+)?|[0-9]?\\d(?:\\.\\d+)?)(?![\\d.])",
    )
    private val categoryCharacters = Regex("[^a-z0-9-]+")
    private val lineBreaks = Regex("[\\r\\n\\u2028\\u2029]+")
    private val repeatedWhitespace = Regex("\\s+")

    @Synchronized
    fun record(context: Context, category: String, retryable: Boolean, message: String) {
        try {
            val file = logFile(context)
            file.parentFile?.mkdirs()
            val lines = newestLines(file, MAX_LINES - 1).toMutableList()
            lines += listOf(
                Instant.now().toString(),
                BuildConfig.VERSION_NAME,
                sanitizeCategory(category),
                retryable.toString(),
                sanitizeMessage(message),
            ).joinToString(" | ")
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n"))
        } catch (_: Exception) {
            // Diagnostics must never crash an application failure path.
        }
    }

    @Synchronized
    fun readRecent(context: Context, maxLines: Int = 40): List<String> = try {
        newestLines(logFile(context), maxLines.coerceIn(0, MAX_LINES))
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            logFile(context).delete()
        } catch (_: Exception) {
            // Diagnostics cleanup is best effort.
        }
    }

    private fun sanitizeCategory(category: String): String = category
        .lowercase(Locale.ROOT)
        .replace(categoryCharacters, "-")
        .trim('-')
        .take(32)
        .ifBlank { "unknown" }

    private fun sanitizeMessage(message: String): String = message
        .replace(lineBreaks, " ")
        .replace(bearerToken, "Bearer [REDACTED]")
        .replace(secretKeyToken, "[REDACTED-KEY]")
        .replace(apiKey, "api-key=[REDACTED]")
        .replace(sensitivePayload) { match -> "${match.groupValues[1].lowercase()}=[REDACTED]" }
        .replace(labeledCoordinatePair, "coordinates=[REDACTED]")
        .replace(reversedLabeledCoordinatePair, "coordinates=[REDACTED]")
        .replace(labeledCoordinate, "coordinate=[REDACTED]")
        .replace(bareCoordinatePair, "coordinates=[REDACTED]")
        .replace(repeatedWhitespace, " ")
        .trim()
        .take(MAX_MESSAGE_LENGTH)

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

    private fun logFile(context: Context): File =
        File(context.filesDir, "diagnostics/operational_failures.log")
}
