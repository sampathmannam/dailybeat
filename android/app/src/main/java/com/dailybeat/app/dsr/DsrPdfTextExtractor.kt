package com.dailybeat.app.dsr

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

class DsrPdfTextExtractor {
    fun extract(file: File): List<String> = PDDocument.load(file).use { document ->
        require(!document.isEncrypted) { "Password-protected PDFs are not supported." }
        require(document.numberOfPages in 1..250) { "The PDF must contain between 1 and 250 pages." }
        val stripper = PDFTextStripper().apply {
            sortByPosition = true
            addMoreFormatting = true
        }
        (1..document.numberOfPages).map { pageNumber ->
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            stripper.getText(document)
                .replace("\u0000", "")
                .trim()
        }
    }
}
