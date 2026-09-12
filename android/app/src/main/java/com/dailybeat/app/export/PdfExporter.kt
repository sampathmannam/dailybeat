package com.dailybeat.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.dailybeat.app.util.AppStorage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PdfExporter(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val LINE_HEIGHT = 16f
        private const val FOOTER_RESERVE = 40f
    }

    fun exportDairy(
        officerName: String,
        dairyText: String,
        date: LocalDate = LocalDate.now(),
        supervisorName: String = "",
        destination: File? = null,
    ): File {
        val safeOfficer = officerName.trim().ifBlank { "IPS Officer" }
        val safeSupervisor = supervisorName.trim()
        val safeText = dairyText.trim().ifBlank { "No diary content." }
        val document = PdfDocument()
        try {
            val titlePaint = Paint().apply {
                textSize = 18f
                isFakeBoldText = true
            }
            val bodyPaint = Paint().apply { textSize = 12f }
            val footerPaint = Paint().apply { textSize = 11f }

            val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val allLines = buildContentLines(safeText, bodyPaint, PAGE_WIDTH - MARGIN * 2)

            var pageNumber = 1
            var lineIndex = 0
            var page = startPage(
                document,
                pageNumber,
                dateStr,
                safeOfficer,
                safeSupervisor,
                titlePaint,
                bodyPaint,
            )
            var canvas = page.canvas
            var y = MARGIN + 72f

            while (lineIndex < allLines.size) {
                val maxY = PAGE_HEIGHT - MARGIN - FOOTER_RESERVE
                if (y + LINE_HEIGHT > maxY) {
                    finishPage(document, page, canvas, pageNumber, footerPaint)
                    pageNumber++
                    page = startPage(
                        document,
                        pageNumber,
                        dateStr,
                        safeOfficer,
                        safeSupervisor,
                        titlePaint,
                        bodyPaint,
                    )
                    canvas = page.canvas
                    y = MARGIN + 72f
                }
                canvas.drawText(allLines[lineIndex], MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
                lineIndex++
            }

            canvas.drawText(
                "Page $pageNumber — Submitted via DailyBeat.",
                MARGIN,
                PAGE_HEIGHT - MARGIN,
                footerPaint,
            )
            document.finishPage(page)

            val out = destination ?: AppStorage.outputFile(context, "$dateStr.pdf")
            out.parentFile?.mkdirs()
            val temp = File.createTempFile("dailybeat-pdf-", ".tmp", out.parentFile ?: context.cacheDir)
            try {
                temp.outputStream().use { document.writeTo(it) }
                if (!temp.renameTo(out)) {
                    temp.copyTo(out, overwrite = true)
                    AppStorage.clearSensitiveFile(temp)
                }
            } finally {
                AppStorage.clearSensitiveFile(temp)
            }
            return out
        } finally {
            document.close()
        }
    }

    private fun startPage(
        document: PdfDocument,
        pageNumber: Int,
        dateStr: String,
        officerName: String,
        supervisorName: String,
        titlePaint: Paint,
        bodyPaint: Paint,
    ): PdfDocument.Page {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        var y = MARGIN + 20f
        canvas.drawText("Daily Diary — $dateStr", MARGIN, y, titlePaint)
        y += 28f
        canvas.drawText("Officer: $officerName", MARGIN, y, bodyPaint)
        if (supervisorName.isNotBlank()) {
            y += LINE_HEIGHT
            canvas.drawText("Supervisor: $supervisorName", MARGIN, y, bodyPaint)
        }
        return page
    }

    private fun finishPage(
        document: PdfDocument,
        page: PdfDocument.Page,
        canvas: Canvas,
        pageNumber: Int,
        footerPaint: Paint,
    ) {
        canvas.drawText(
            "Page $pageNumber — Submitted via DailyBeat.",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            footerPaint,
        )
        document.finishPage(page)
    }

    private fun buildContentLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        for (rawLine in text.split("\n")) {
            lines.addAll(wrapLine(rawLine, paint, maxWidth))
        }
        return lines
    }

    fun wrapLine(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isBlank()) return listOf("")
        require(maxWidth > 0f) { "maxWidth must be positive." }
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            if (paint.measureText(word) > maxWidth) {
                if (current.isNotEmpty()) {
                    lines.add(current)
                }
                var remaining = word
                while (paint.measureText(remaining) > maxWidth) {
                    val count = fittingPrefixLength(remaining, paint, maxWidth)
                    lines.add(remaining.take(count))
                    remaining = remaining.drop(count)
                }
                current = remaining
                continue
            }
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines.add(current)
                current = word
            }
        }
        if (current.isNotEmpty()) lines.add(current)
        return lines
    }

    /**
     * Returns the longest prefix that fits. Using measured binary search rather than trusting
     * Paint.breakText keeps wrapping deterministic across Android, Robolectric, and vendor fonts.
     */
    private fun fittingPrefixLength(text: String, paint: Paint, maxWidth: Float): Int {
        var low = 1
        var high = text.length
        var best = 0
        while (low <= high) {
            val midpoint = (low + high).ushr(1)
            val safeMidpoint = if (
                midpoint < text.length &&
                Character.isHighSurrogate(text[midpoint - 1]) &&
                Character.isLowSurrogate(text[midpoint])
            ) {
                midpoint - 1
            } else {
                midpoint
            }
            if (safeMidpoint > 0 && paint.measureText(text, 0, safeMidpoint) <= maxWidth) {
                best = maxOf(best, safeMidpoint)
                low = midpoint + 1
            } else {
                high = midpoint - 1
            }
        }
        return best.takeIf { it > 0 } ?: Character.charCount(text.codePointAt(0))
    }
}
