package com.dailybeat.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class PdfExporter(private val context: Context) {

  fun exportDairy(officerName: String, dairyText: String, date: LocalDate = LocalDate.now()): File {
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val document = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page = document.startPage(pageInfo)
    val canvas: Canvas = page.canvas

    val titlePaint = Paint().apply {
      textSize = 18f
      isFakeBoldText = true
    }
    val bodyPaint = Paint().apply { textSize = 12f }
    val footerPaint = Paint().apply { textSize = 11f }

    val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    var y = margin + 20f
    canvas.drawText("Daily Diary — $dateStr", margin, y, titlePaint)
    y += 28f
    canvas.drawText("Officer: $officerName", margin, y, bodyPaint)
    y += 24f

    val lines = dairyText.split("\n")
    for (line in lines) {
      val wrapped = wrapLine(line, bodyPaint, pageWidth - margin * 2)
      for (part in wrapped) {
        if (y > pageHeight - margin - 40f) break
        canvas.drawText(part, margin, y, bodyPaint)
        y += 16f
      }
    }

    y = pageHeight - margin
    canvas.drawText("Submitted via DailyBeat (offline).", margin, y, footerPaint)

    document.finishPage(page)

    val dir = File(context.getExternalFilesDir(null), "DailyBeat")
    dir.mkdirs()
    val out = File(dir, "$dateStr.pdf")
    out.outputStream().use { document.writeTo(it) }
    document.close()
    return out
  }

  private fun wrapLine(text: String, paint: Paint, maxWidth: Float): List<String> {
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
