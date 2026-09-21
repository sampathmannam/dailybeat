package com.dailybeat.app.export

import android.content.Context
import com.dailybeat.app.capture.CaptureStorageGate
import com.dailybeat.app.util.AppStorage
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageExporter(
    private val context: Context,
    private val sharing: DiaryShareService,
    private val pdfExporter: PdfExporter,
) {
    suspend fun exportWeekPackage(previews: List<DiarySharePreview>): File {
        val generation = CaptureStorageGate.dataGeneration.get()
        return CaptureStorageGate.writeIfCurrent(generation) {
            require(previews.isNotEmpty()) { "No saved diaries in the last seven days." }
            previews.forEach { sharing.requireCurrent(it) }
            val zipFile = AppStorage.outputFile(context, "dailybeat-export-${UUID.randomUUID()}.zip")
            val tempZip = File.createTempFile("dailybeat-export-", ".tmp", AppStorage.exportStagingDir(context))
            try {
                ZipOutputStream(tempZip.outputStream()).use { zip ->
                    previews.forEach { entry ->
                        zip.putNextEntry(ZipEntry("diaries/${entry.date}.txt"))
                        zip.write(entry.text.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                        val pdf = File.createTempFile("dailybeat-${entry.date}-", ".pdf", AppStorage.exportStagingDir(context))
                        try {
                            pdfExporter.exportDairy(entry.author, entry.text, entry.date, entry.supervisor,
                                destination = pdf, profile = entry.profile)
                            zip.putNextEntry(ZipEntry("diaries/${entry.date}.pdf"))
                            pdf.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        } finally { AppStorage.clearSensitiveFile(pdf) }
                    }
                }
                previews.forEach { sharing.requireCurrent(it) }
                if (!tempZip.renameTo(zipFile)) {
                    tempZip.copyTo(zipFile, overwrite = false)
                }
                zipFile
            } catch (error: Exception) {
                AppStorage.clearSensitiveFile(zipFile)
                throw error
            } finally { AppStorage.clearSensitiveFile(tempZip) }
        }
    }
}
