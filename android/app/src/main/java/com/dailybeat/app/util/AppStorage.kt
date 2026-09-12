package com.dailybeat.app.util

import android.content.Context
import java.io.File

/**
 * Where DailyBeat keeps the files it produces (PDFs, exports, the capture audit log).
 *
 * External app storage is preferred because it is what the FileProvider shares from, but it is
 * null while external storage is unavailable, so fall back to internal storage rather than
 * writing to a relative path that fails at the first mkdirs().
 */
object AppStorage {

    private const val DIR_NAME = "DailyBeat"
    private const val MAX_OUTPUT_NAME_LENGTH = 128

    fun outputDir(context: Context): File {
        val parent = context.getExternalFilesDir(null) ?: context.filesDir
        return File(parent, DIR_NAME).apply { mkdirs() }
    }

    fun outputFile(context: Context, name: String): File = outputFileIn(outputDir(context), name)

    internal fun outputFileIn(directory: File, name: String): File {
        require(name.isNotBlank() && name.length <= MAX_OUTPUT_NAME_LENGTH) {
            "Output filename must contain between 1 and $MAX_OUTPUT_NAME_LENGTH characters."
        }
        require(name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name) {
            "Output filename must not contain a path."
        }

        val safeDirectory = directory.canonicalFile
        val candidate = File(safeDirectory, name).canonicalFile
        require(candidate.parentFile == safeDirectory) { "Output filename escapes the app directory." }
        return candidate
    }

    /**
     * Removes a temporary file containing diary data. If the filesystem refuses deletion, clear
     * the contents so a failed cleanup cannot leave the sensitive payload behind indefinitely.
     */
    fun clearSensitiveFile(file: File) {
        runCatching {
            if (file.exists() && !file.delete()) {
                file.outputStream().use { }
            }
        }
    }
}
