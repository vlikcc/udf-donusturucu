package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
// play-services-mlkit-text-recognition (unbundled) modülünde TextRecognizerOptions, bundled
// com.google.mlkit:text-recognition'daki gibi com.google.mlkit.vision.text altında değil,
// .latin alt paketindedir.
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.velikececi.udfdonusturucu.core.converters.PdfConverter
import com.velikececi.udfdonusturucu.core.converters.UdfCreator
import com.velikececi.udfdonusturucu.core.converters.WordConverter
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.UdfContent
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.model.UdfSection
import kotlinx.coroutines.tasks.await
import java.io.File

sealed class OcrException(message: String) : Exception(message) {
    class CannotOpen : OcrException("Dosya açılamadı.")
    class NoText : OcrException("Belgede okunabilir metin bulunamadı.")
    class RecognitionFailed(detail: String?) : OcrException("Metin tanıma başarısız: ${detail ?: "bilinmeyen hata"}")
}

/**
 * OCRService.swift'in Kotlin karşılığı. iOS Vision çerçevesi (`tr-TR`+`en-US`,
 * `usesLanguageCorrection`) kullanıyordu; ML Kit v2'de dil seçeneği/dil-özel düzeltme YOKTUR —
 * `TextRecognizerOptions.DEFAULT_OPTIONS` (Latin script) Türkçe diakritikleri (ç ğ ı İ ö ş ü)
 * kapsar ama iOS'un `tr-TR` sonucunun biraz altında kalabilir.
 *
 * Sayfa rasterizasyonu için [PdfRasterizer] kullanılır (~300 dpi, uzun kenar 4000 px'te
 * sınırlı — iOS'un `min(300/72, 4000/max(w,h))` ölçek hesabıyla aynı).
 */
object OcrService {

    private const val TARGET_DPI = 300f
    private const val MAX_PIXELS = 4000

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * PDF sayfalarını tek tek tanır. [PdfRasterizer.rasterize]'ın senkron geri çağrısı içinde
     * `Tasks.await(...)` (bloklayan) kullanılır — bu fonksiyon her zaman bir arka plan
     * dispatcher'ından (`Dispatchers.Default`) çağrıldığından ana iş parçacığını asla bloklamaz;
     * askıda (suspend) `.await()`'i senkron bir geri çağrı içine iç içe `runBlocking` ile
     * sokmaktan daha temizdir.
     */
    suspend fun recognizeText(pdfFile: File, context: Context, onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }): String {
        val pageTexts = mutableListOf<String>()
        var failure: Exception? = null

        try {
            PdfRasterizer.rasterize(pdfFile, context, TARGET_DPI, MAX_PIXELS) { index, total, page ->
                if (failure == null) {
                    try {
                        onProgress(index + 1, total)
                        pageTexts += recognizeBitmapBlocking(page.bitmap)
                    } catch (e: Exception) {
                        failure = e
                    }
                }
                page.bitmap.recycle()
            }
        } catch (e: PdfRasterizer.UnsupportedPdfException) {
            throw OcrException.CannotOpen()
        } catch (e: Exception) {
            throw OcrException.CannotOpen()
        }

        failure?.let { throw OcrException.RecognitionFailed(it.message) }

        val combined = pageTexts.filter { it.isNotBlank() }.joinToString("\n\n")
        if (combined.isBlank()) throw OcrException.NoText()
        return combined
    }

    suspend fun recognizeText(imageUri: Uri, context: Context): String {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (e: Exception) {
            throw OcrException.CannotOpen()
        }
        val text = try {
            recognizer.process(image).await().text
        } catch (e: Exception) {
            throw OcrException.RecognitionFailed(e.message)
        }
        if (text.isBlank()) throw OcrException.NoText()
        return text
    }

    private fun recognizeBitmapBlocking(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = Tasks.await(recognizer.process(image))
        return result.textBlocks
            .sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))
            .joinToString("\n") { block -> block.lines.joinToString("\n") { it.text } }
    }

    // MARK: - Dışa aktarım

    fun exportDocx(text: String, baseName: String, context: Context): File =
        WordConverter.convert(plainTextDocument(text, baseName), context)

    fun exportPdf(text: String, baseName: String, context: Context): File =
        PdfConverter.convert(plainTextDocument(text, baseName), context)

    fun exportUdf(text: String, baseName: String, context: Context): File {
        val paragraphs = text.split("\n").map { line ->
            ExtractedParagraph(
                runs = listOf(ExtractedTextRun(line, isBold = false, isItalic = false, isUnderline = false, fontSize = 12f, fontFamily = "Times New Roman")),
                alignment = 3,
            )
        }
        return UdfCreator.create("OCR_$baseName", paragraphs, context)
    }

    private fun plainTextDocument(text: String, baseName: String) = UdfDocument(
        fileName = baseName,
        content = UdfContent(
            text = text,
            rawContent = text,
            contentType = UdfContentType.PLAIN_TEXT,
            sections = listOf(UdfSection(title = null, body = text, level = 0)),
            tables = emptyList(),
            isRtf = false,
            paragraphs = emptyList(),
        ),
        metadata = null,
        pageFormat = null,
    )
}
