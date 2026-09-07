package com.dailybeat.app.dsr

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

data class StoredDsrDocument(
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val originalFileName: String,
    val wasAlreadyStored: Boolean,
)

class DsrDocumentStore(
    private val context: Context,
    private val maxBytes: Long = 25L * 1024L * 1024L,
) {
    private val directory: File
        get() = File(context.filesDir, "dsr_imports").also { require(it.exists() || it.mkdirs()) }

    fun copyFrom(uri: Uri): StoredDsrDocument {
        val displayName = queryDisplayName(uri) ?: "operational-report.pdf"
        val temporary = File(directory, "incoming-${UUID.randomUUID()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "The selected document could not be opened." }
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "The PDF is larger than the 25 MB import limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            require(total >= 5) { "The selected file is empty." }
            val signature = temporary.inputStream().use { input ->
                ByteArray(5).also { bytes -> input.read(bytes) }.toString(Charsets.US_ASCII)
            }
            require(signature == "%PDF-") { "The selected file is not a valid PDF document." }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val destination = File(directory, "$hash.pdf")
            val existed = destination.exists()
            if (existed) {
                check(temporary.delete()) { "Could not remove the temporary import file." }
            } else {
                check(temporary.renameTo(destination)) { "Could not move the PDF into private app storage." }
            }
            return StoredDsrDocument(
                file = destination,
                sha256 = hash,
                sizeBytes = total,
                originalFileName = sanitizeFileName(displayName),
                wasAlreadyStored = existed,
            )
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun removeIfUnreferenced(document: StoredDsrDocument) {
        if (!document.wasAlreadyStored) document.file.delete()
    }

    private fun queryDisplayName(uri: Uri): String? = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun sanitizeFileName(value: String): String {
        val clean = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
            .take(120)
        return clean.ifBlank { "operational-report.pdf" }
    }
}
