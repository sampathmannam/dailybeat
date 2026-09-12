package com.dailybeat.app.export

import android.content.Context
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.repo.DiaryRepository
import com.dailybeat.app.util.AppStorage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageExporter(
    private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val pdfExporter: PdfExporter,
) {

    suspend fun exportWeekPackage(officerName: String, supervisorName: String): File {
        val stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val zipFile = AppStorage.outputFile(context, "dailybeat-export-$stamp.zip")
        val tempZip = File.createTempFile(
            "dailybeat-export-",
            ".tmp",
            zipFile.parentFile ?: context.cacheDir,
        )

        try {
            ZipOutputStream(tempZip.outputStream()).use { zip ->
                val auditLines = CaptureAuditLog.readRecent(context, 500)
                zip.putNextEntry(ZipEntry("capture_audit.log"))
                zip.write(auditLines.joinToString("\n").toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()

                // A week is seven calendar days, not the seven most recently stored diaries.
                // The old query could export months-old or future-dated entries unexpectedly.
                val diaries = diaryRepository.weekEnding(LocalDate.parse(stamp))
                diaries.forEach { entry ->
                    zip.putNextEntry(ZipEntry("diaries/${entry.dateKey}.txt"))
                    zip.write(entry.text.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()

                    val pdf = File.createTempFile("dailybeat-${entry.dateKey}-", ".pdf", context.cacheDir)
                    try {
                        pdfExporter.exportDairy(
                            officerName = officerName,
                            dairyText = entry.text,
                            date = LocalDate.parse(entry.dateKey),
                            supervisorName = supervisorName,
                            destination = pdf,
                        )
                        zip.putNextEntry(ZipEntry("diaries/${entry.dateKey}.pdf"))
                        pdf.inputStream().use { input -> input.copyTo(zip) }
                        zip.closeEntry()
                    } finally {
                        AppStorage.clearSensitiveFile(pdf)
                    }
                }
            }
            if (!tempZip.renameTo(zipFile)) {
                tempZip.copyTo(zipFile, overwrite = true)
                AppStorage.clearSensitiveFile(tempZip)
            }
            return zipFile
        } finally {
            AppStorage.clearSensitiveFile(tempZip)
        }
    }
}
