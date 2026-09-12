package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * `PDFBoxResourceLoader.init(context)`'in tek noktadan, thread-safe ve tekrarsız çağrılması.
 * [PdfExtractor] ile birlikte [com.velikececi.udfdonusturucu.core.tools.PdfRasterizer],
 * [com.velikececi.udfdonusturucu.core.tools.PdfToolsService] ve
 * [com.velikececi.udfdonusturucu.core.tools.MergeService] tarafından paylaşılır — hepsi aynı
 * `PDDocument`/`PDFRenderer` altyapısını kullandığından yazı tipi kaynakları bir kez yüklenir.
 */
object PdfBoxInit {
    @Volatile
    private var initialized = false

    fun ensure(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                initialized = true
            }
        }
    }
}
