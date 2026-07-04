package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import java.io.File

/** PDFConverter.swift'teki `outputDirectory()` karşılığı — tüm dönüştürücüler tarafından paylaşılır. */
object OutputPaths {
    fun outputDirectory(context: Context): File =
        File(context.filesDir, "ConvertedFiles").apply { mkdirs() }

    /** [originalFileName]'in uzantısız gövdesini alıp benzersiz bir çıktı dosyası üretir. */
    fun outputFile(context: Context, originalFileName: String, extension: String): File {
        val baseName = originalFileName.substringBeforeLast('.', originalFileName)
        return File(outputDirectory(context), "$baseName.$extension")
    }
}
