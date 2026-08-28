package com.dailybeat.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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

    fun exportDairy(officerName: String, dairyText: String, date: LocalDate = LocalDate.now()): File {
        val safeOfficer = officerName.trim().ifBlank { "IPS Officer" }
        val safeText = dairyText.trim().ifBlank { "No diary content." }
        val document = PdfDocument()
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
            var page = startPage(document, pageNumber, dateStr, safeOfficer, titlePaint, bodyPaint)
        var canvas = page.canvas
        var y = MARGIN + 72f

        while (lineIndex < allLines.size) {
            val maxY = PAGE_HEIGHT - MARGIN - FOOTER_RESERVE
            if (y + LINE_HEIGHT > maxY) {
                finishPage(document, page, canvas, pageNumber, footerPaint)
                pageNumber++
                page = startPage(document, pageNumber, dateStr, safeOfficer, titlePaint, bodyPaint)
                canvas = page.canvas
                y = MARGIN + 72f
            }
            canvas.drawText(allLines[lineIndex], MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
            lineIndex++
        }

        canvas.drawText(
            "Page $pageNumber — Submitted via DailyBeat (offline).",
            MARGIN,
            PAGE_HEIGHT - MARGIN,
            footerPaint,
        )
        document.finishPage(page)

        val dir = File(context.getExternalFilesDir(null), "DailyBeat")
        dir.mkdirs()
        val out = File(dir, "$dateStr.pdf")
        out.outputStream().use { document.writeTo(it) }
        document.close()
        return out
    }

    private fun startPage(
        document: PdfDocument,
        pageNumber: Int,
        dateStr: String,
        officerName: String,
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
            "Page $pageNumber — Submitted via DailyBeat (offline).",
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
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = ""
        for (word in words) {
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
}
