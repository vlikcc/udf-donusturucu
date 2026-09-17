package com.velikececi.udfdonusturucu.converter

import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

object DocxExtractor {

    fun extractText(file: File): String {
        FileInputStream(file).use { fis ->
            val doc = XWPFDocument(fis)
            val sb = StringBuilder()
            for (p in doc.paragraphs) {
                val text = p.text
                if (!text.isNullOrBlank()) {
                    sb.append(text).append("\n")
                }
            }
            for (table in doc.tables) {
                for (row in table.rows) {
                    for (cell in row.tableCells) {
                        sb.append(cell.text).append("\t")
                    }
                    sb.append("\n")
                }
            }
            return sb.toString().trim()
        }
    }
}
