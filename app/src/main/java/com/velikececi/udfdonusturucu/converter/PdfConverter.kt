package com.velikececi.udfdonusturucu.converter

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfConverter {

    fun extractTextFromPdf(file: File): String {
        PDDocument.load(file).use { doc ->
            val stripper = PDFTextStripper()
            return stripper.getText(doc).trim()
        }
    }

    fun createPdfFromText(text: String, outputFile: File) {
        val document = PDDocument()
        try {
            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            val margin = 50f
            val startY = PDRectangle.A4.height - margin
            val leading = 15f
            var currentY = startY

            var contentStream = PDPageContentStream(document, page)
            contentStream.beginText()
            contentStream.setFont(PDType1Font.HELVETICA, 10f)
            contentStream.newLineAtOffset(margin, currentY)

            val lines = text.split("\n")
            for (line in lines) {
                if (currentY <= margin + leading) {
                    contentStream.endText()
                    contentStream.close()

                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)
                    currentY = startY

                    contentStream = PDPageContentStream(document, page)
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, 10f)
                    contentStream.newLineAtOffset(margin, currentY)
                }

                // Helvetica standart karakter seti için güvenli dönüştürme
                val safeLine = sanitizeForPdf(line)
                contentStream.showText(safeLine)
                contentStream.newLineAtOffset(0f, -leading)
                currentY -= leading
            }

            contentStream.endText()
            contentStream.close()

            document.save(outputFile)
        } finally {
            document.close()
        }
    }

    private fun sanitizeForPdf(text: String): String {
        return text.replace("ğ", "g").replace("Ğ", "G")
            .replace("ş", "s").replace("Ş", "S")
            .replace("ı", "i").replace("İ", "I")
            .replace("ç", "c").replace("Ç", "C")
            .replace("ö", "o").replace("Ö", "O")
            .replace("ü", "u").replace("Ü", "U")
            .replace(Regex("[^\\x20-\\x7E]"), " ")
    }
}
