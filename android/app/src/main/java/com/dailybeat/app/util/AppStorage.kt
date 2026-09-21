package com.dailybeat.app.util

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Shareable documents live in internal app storage, including on Android 8–10 where another
 * app's storage permission can expose external app files. Only the exports child is shared.
 */
object AppStorage {

    private const val DIR_NAME = "DailyBeat"
    private const val MAX_OUTPUT_NAME_LENGTH = 128

    fun outputDir(context: Context): File {
        return requireDirectory(File(context.filesDir, "$DIR_NAME/exports"))
    }

    fun exportStagingDir(context: Context): File =
        requireDirectory(File(context.cacheDir, "diary-export-staging"))

    /** Historical storage roots must all be considered, even when external storage is mounted. */
    internal fun legacyOutputDirs(context: Context): List<File> =
        (listOf(File(context.filesDir, DIR_NAME)) +
            context.getExternalFilesDirs(null).filterNotNull().map { File(it, DIR_NAME) })
            .distinctBy { it.absolutePath }

    /** Preserve completed exports on upgrade, and remove only known unfinished export files. */
    fun migrateLegacyOutputs(context: Context): Boolean {
        var succeeded = true
        val output = outputDir(context)
        legacyOutputDirs(context).forEach { directory ->
            if (Files.isSymbolicLink(directory.toPath())) {
                succeeded = clearSensitiveFileVerified(directory) && succeeded
                return@forEach
            }
            if (!directory.exists()) return@forEach
            val files = directory.listFiles()
            if (files == null) {
                succeeded = false
                return@forEach
            }
            files.forEach { source ->
                // External storage can contain attacker-created links. Never copy their targets.
                if (Files.isSymbolicLink(source.toPath())) {
                    succeeded = clearSensitiveFileVerified(source) && succeeded
                } else if (source.isFile && source.extension.lowercase() in setOf("pdf", "zip")) {
                    val moved = runCatching {
                        val target = outputFileIn(output, "legacy-${UUID.randomUUID()}.${source.extension.lowercase()}")
                        if (!source.renameTo(target)) {
                            val temporary = File.createTempFile("migration-", ".tmp", exportStagingDir(context))
                            try {
                                source.inputStream().use { input ->
                                    temporary.outputStream().use { destination -> input.copyTo(destination) }
                                }
                                check(temporary.renameTo(target)) { "Unable to finish export migration." }
                            } finally {
                                clearSensitiveFile(temporary)
                            }
                            check(clearSensitiveFileVerified(source)) { "Unable to clear legacy export." }
                        }
                    }.isSuccess
                    succeeded = moved && succeeded
                } else if (source.isFile && source.name.startsWith("dailybeat-") && source.extension == "tmp") {
                    succeeded = clearSensitiveFileVerified(source) && succeeded
                }
            }
        }
        return succeeded
    }

    /** Explicit phone erasure includes both previous storage choices and interrupted exports. */
    fun clearGeneratedFiles(context: Context): Boolean {
        var succeeded = true
        legacyOutputDirs(context).forEach { directory ->
            succeeded = clearSensitiveDirectoryVerified(directory) && succeeded
        }
        succeeded = clearSensitiveDirectoryVerified(File(context.cacheDir, "diary-export-staging")) && succeeded
        // Previous versions placed temporary per-day PDFs directly in cacheDir.
        val legacyTemporaryPdf = Regex("dailybeat-\\d{4}-\\d{2}-\\d{2}--?\\d+\\.pdf")
        val cachedFiles = context.cacheDir.listFiles()
        if (cachedFiles == null) return false
        cachedFiles.filter { legacyTemporaryPdf.matches(it.name) }.forEach { file ->
            succeeded = clearSensitiveFileVerified(file) && succeeded
        }
        return succeeded
    }

    private fun requireDirectory(directory: File): File = directory.apply {
        check(isDirectory || mkdirs()) { "Unable to open private document storage." }
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
        clearSensitiveFileVerified(file)
    }

    /** Same cleanup with a result for explicit privacy-erasure flows that must report failure. */
    fun clearSensitiveFileVerified(file: File): Boolean = runCatching {
        if (Files.isSymbolicLink(file.toPath())) return@runCatching file.delete()
        if (file.exists() && !file.delete()) {
            if (!file.isFile) return@runCatching false
            file.outputStream().use { }
        }
        !file.exists() || file.length() == 0L
    }.getOrDefault(false)

    /** Delete only this app-owned tree; links are removed without following or truncating them. */
    internal fun clearSensitiveDirectoryVerified(directory: File): Boolean = runCatching {
        if (Files.isSymbolicLink(directory.toPath()) || !directory.isDirectory) {
            return@runCatching clearSensitiveFileVerified(directory)
        }
        val children = directory.listFiles() ?: return@runCatching false
        var cleared = true
        children.forEach { child ->
            cleared = clearSensitiveDirectoryVerified(child) && cleared
        }
        cleared && directory.delete()
    }.getOrDefault(false)
}
