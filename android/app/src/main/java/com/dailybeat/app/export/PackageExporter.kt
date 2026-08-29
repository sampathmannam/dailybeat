package com.dailybeat.app.export

import android.content.Context
import com.dailybeat.app.audit.CaptureAuditLog
import com.dailybeat.app.data.repo.DiaryRepository
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackageExporter(
    private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val pdfExporter: PdfExporter,
) {

    suspend fun exportWeekPackage(officerName: String, supervisorName: String): File {
        val dir = File(context.getExternalFilesDir(null), "DailyBeat")
        dir.mkdirs()
        val stamp = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val zipFile = File(dir, "dailybeat-export-$stamp.zip")

        ZipOutputStream(zipFile.outputStream()).use { zip ->
            val auditLines = CaptureAuditLog.readRecent(context, 500)
            zip.putNextEntry(ZipEntry("capture_audit.log"))
            zip.write(auditLines.joinToString("\n").toByteArray())
            zip.closeEntry()

            val diaries = diaryRepository.recentSync(30)
            diaries.forEach { entry ->
                zip.putNextEntry(ZipEntry("diaries/${entry.dateKey}.txt"))
                zip.write(entry.text.toByteArray())
                zip.closeEntry()

                val pdf = pdfExporter.exportDairy(officerName, entry.text, LocalDate.parse(entry.dateKey), supervisorName)
                zip.putNextEntry(ZipEntry("diaries/${entry.dateKey}.pdf"))
                pdf.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return zipFile
    }
}
