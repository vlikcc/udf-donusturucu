package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer as PdfBoxRenderer
import com.velikececi.udfdonusturucu.core.converters.PdfBoxInit
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * PDF sayfalarını [Bitmap]'e dönüştüren paylaşılan bileşen — [PdfToolsService] (sıkıştırma) ve
 * OCR aracı (Faz 6) tarafından kullanılır. iOS'un `PDFPage.thumbnail(of:for:)` (PDFKit) karşılığı.
 *
 * Öncelik native `android.graphics.pdf.PdfRenderer`'dadır (Skia, donanım hızlandırmalı — PDFBox'ın
 * saf-Java `PageDrawer`'ından çok daha hızlı). Native renderer'ın açamadığı (bozuk/nadir yapılı)
 * dosyalarda PDFBox `PDFRenderer`'a düşülür.
 */
object PdfRasterizer {

    data class PageBitmap(val bitmap: Bitmap, val widthPoints: Float, val heightPoints: Float)

    class UnsupportedPdfException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * [file]'daki her sayfayı [targetDpi] yoğunluğunda, uzun kenarı [maxPixels]'i aşmayacak
     * şekilde rasterize eder. [onPage] her sayfa render edildikten hemen sonra çağrılır — çağıran
     * taraf bitmap'i işleyip (ör. JPEG'e kodlayıp) `recycle()` ETMELİDİR; bu fonksiyon belleği
     * biriktirmez, sayfaları tek tek üretip tüketilmesini bekler.
     */
    fun rasterize(
        file: File,
        context: Context,
        targetDpi: Float,
        maxPixels: Int,
        onPage: (index: Int, total: Int, page: PageBitmap) -> Unit,
    ) {
        try {
            rasterizeNative(file, targetDpi, maxPixels, onPage)
        } catch (e: SecurityException) {
            throw UnsupportedPdfException("Bu PDF parola korumalı, işlem yapılamıyor.", e)
        } catch (e: Exception) {
            // Native PdfRenderer bozuk/desteklenmeyen bir yapıda başarısız olabilir — PDFBox'ın
            // daha toleranslı (saf Java) ayrıştırıcısına düş.
            rasterizeWithPdfBox(file, context, targetDpi, maxPixels, onPage)
        }
    }

    private fun rasterizeNative(
        file: File,
        targetDpi: Float,
        maxPixels: Int,
        onPage: (Int, Int, PageBitmap) -> Unit,
    ) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                for (index in 0 until total) {
                    renderer.openPage(index).use { page ->
                        val widthPt = page.width.toFloat()
                        val heightPt = page.height.toFloat()
                        val scale = min(targetDpi / 72f, maxPixels / max(widthPt, heightPt))
                        val widthPx = max(1, (widthPt * scale).toInt())
                        val heightPx = max(1, (heightPt * scale).toInt())
                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        // PdfRenderer arka planı boyamaz — saydam bitmap JPEG'e siyah kodlanır.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        onPage(index, total, PageBitmap(bitmap, widthPt, heightPt))
                    }
                }
            }
        }
    }

    private fun rasterizeWithPdfBox(
        file: File,
        context: Context,
        targetDpi: Float,
        maxPixels: Int,
        onPage: (Int, Int, PageBitmap) -> Unit,
    ) {
        PdfBoxInit.ensure(context)
        PDDocument.load(file).use { document ->
            val renderer = PdfBoxRenderer(document)
            val total = document.numberOfPages
            for (index in 0 until total) {
                val mediaBox = document.getPage(index).mediaBox
                val widthPt = mediaBox.width
                val heightPt = mediaBox.height
                val dpi = min(targetDpi, maxPixels / max(widthPt, heightPt) * 72f)
                val bitmap = renderer.renderImageWithDPI(index, dpi, ImageType.RGB)
                onPage(index, total, PageBitmap(bitmap, widthPt, heightPt))
            }
        }
    }
}
