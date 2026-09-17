package com.velikececi.udfdonusturucu.converter

import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream

object WordConverter {

    fun createDocxFromText(text: String, outputFile: File) {
        val document = XWPFDocument()
        val lines = text.split("\n")
        for (line in lines) {
            val paragraph = document.createParagraph()
            paragraph.alignment = ParagraphAlignment.LEFT
            val run = paragraph.createRun()
            run.setText(line)
            run.fontSize = 11
            run.fontFamily = "Calibri"
        }
        FileOutputStream(outputFile).use { fos ->
            document.write(fos)
        }
        document.close()
    }
}
