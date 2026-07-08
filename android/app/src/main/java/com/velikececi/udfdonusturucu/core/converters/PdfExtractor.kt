package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.contentstream.operator.OperatorProcessor
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.velikececi.udfdonusturucu.core.model.ExtractedContent
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.ExtractionException
import java.io.File
import java.io.IOException

/**
 * PDFExtractor.swift'in Kotlin karşılığı. iOS tarafı PDFKit'in `page.attributedString`
 * soyutlamasını kullanıyor (font/hizalama bilgisini hazır veriyor); Android'de bunun bir
 * eşdeğeri yok, bu yüzden PdfBox-Android'in `PDFTextStripper`ı alt sınıflanarak:
 *
 * - **Hizalama**, her satırın sol/sağ metin sınırları (`TextPosition.x`) belgedeki EN SIK
 *   görülen sol/sağ kenarlarla (ampirik "sayfa kenar boşluğu" tahmini) karşılaştırılarak
 *   geometrik olarak çıkarılır (bkz. [buildParagraphs]).
 * - **Altı çizili**, PDF içerik akışında `re` (dikdörtgen ekle) operatörü için özel bir
 *   [OperatorProcessor] kaydedilip ince/geniş dikdörtgenler (çizgi adayı) yakalanarak, bu
 *   adayların satırların x/y aralığıyla çakışıp çakışmadığına bakılarak tespit edilir.
 *
 * ⚠️ Altı çizili tespiti CTM (current transformation matrix) dönüşümü UYGULAMAZ — yalnızca
 * sayfa yüksekliğine göre basit bir Y ekseni çevirme yapar. Döndürülmüş/ölçeklenmiş içerik
 * akışlarında (nadir) yanlış negatif üretebilir; standart (rotasyonsuz) PDF'lerde çalışır.
 * Bu, PdfBox-Android'in düşük seviye `OperatorProcessor` API'sini kullanan, bu ortamda
 * derlenip test edilememiş en riskli bölümdür.
 *
 * Kalın/italik PDF fontunun temel adından (`Bold`/`Italic`/`Oblique` alt dizisi) çıkarılır.
 */
object PdfExtractor {

    @Volatile
    private var resourceLoaderInitialized = false

    fun extract(file: File, context: Context): ExtractedContent {
        ensureResourceLoaderInitialized(context)

        val document = try {
            PDDocument.load(file)
        } catch (e: IOException) {
            throw ExtractionException.CannotOpenFile()
        }

        try {
            val pageHeight = runCatching { document.getPage(0).mediaBox.height }.getOrDefault(841.89f)
            val stripper = ChunkCollectingStripper(pageHeight)
            stripper.sortByPosition = true
            stripper.getText(document)

            val paragraphs = buildParagraphs(stripper.chunks, stripper.underlineCandidates)
            val plainText = paragraphs.joinToString("\n") { it.text }
            return ExtractedContent(plainText = plainText, paragraphs = paragraphs)
        } finally {
            document.close()
        }
    }

    private fun ensureResourceLoaderInitialized(context: Context) {
        if (resourceLoaderInitialized) return
        synchronized(this) {
            if (!resourceLoaderInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                resourceLoaderInitialized = true
            }
        }
    }

    // MARK: - Altı çizili adayı: "re" operatörü yakalama

    /** [x, y, genişlik, yükseklik] — ince/geniş bir dikdörtgen (çizgi adayı), sayfa Y'sine göre çevrilmiş. */
    private class UnderlineCandidate(val xMin: Float, val xMax: Float, val yTop: Float, val yBottom: Float)

    private class RectangleOperator(private val onRect: (x: Float, y: Float, w: Float, h: Float) -> Unit) : OperatorProcessor() {
        override fun getName(): String = "re"

        override fun process(operator: Operator, operands: MutableList<COSBase>) {
            if (operands.size < 4) return
            val x = (operands[0] as? COSNumber)?.floatValue() ?: return
            val y = (operands[1] as? COSNumber)?.floatValue() ?: return
            val w = (operands[2] as? COSNumber)?.floatValue() ?: return
            val h = (operands[3] as? COSNumber)?.floatValue() ?: return
            onRect(x, y, w, h)
        }
    }

    // MARK: - Styled chunk collection

    private sealed class Chunk {
        data class Text(
            val text: String,
            val fontName: String,
            val fontSize: Float,
            val x: Float,
            val endX: Float,
            val y: Float,
        ) : Chunk()
        object LineBreak : Chunk()
    }

    private class ChunkCollectingStripper(pageHeight: Float) : PDFTextStripper() {
        val chunks = mutableListOf<Chunk>()
        val underlineCandidates = mutableListOf<UnderlineCandidate>()

        init {
            try {
                addOperator(
                    RectangleOperator { x, y, w, h ->
                        // Altı çizili çizgiler ince ve göreli olarak geniştir (yükseklik << genişlik).
                        if (w > 3f && h in 0.05f..3.0f) {
                            underlineCandidates.add(
                                UnderlineCandidate(
                                    xMin = x,
                                    xMax = x + w,
                                    yTop = pageHeight - (y + h),
                                    yBottom = pageHeight - y,
                                ),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                // "re" operatörü kaydedilemezse (beklenmeyen bir PdfBox-Android sürüm farkı),
                // altı çizili tespiti sessizce devre dışı kalır — metin çıkarımı yine de çalışır.
            }
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isEmpty()) return
            val first = textPositions.firstOrNull()
            val last = textPositions.lastOrNull()
            val fontName = first?.font?.name ?: ""
            val fontSize = first?.fontSizeInPt ?: 12f
            val x = first?.x ?: 0f
            val endX = last?.let { it.x + it.width } ?: x
            val y = first?.y ?: 0f
            chunks.add(Chunk.Text(text, fontName, fontSize, x, endX, y))
        }

        override fun writeWordSeparator() {
            val lastText = chunks.lastOrNull { it is Chunk.Text } as? Chunk.Text
            chunks.add(
                Chunk.Text(
                    text = " ",
                    fontName = lastText?.fontName ?: "",
                    fontSize = lastText?.fontSize ?: 12f,
                    x = lastText?.endX ?: 0f,
                    endX = lastText?.endX ?: 0f,
                    y = lastText?.y ?: 0f,
                ),
            )
        }

        override fun writeLineSeparator() {
            chunks.add(Chunk.LineBreak)
        }
    }

    // MARK: - Paragraph + Run building

    private class LineAccumulator {
        val runs = mutableListOf<ExtractedTextRun>()
        var minX = Float.MAX_VALUE
        var maxX = 0f
        var y = 0f
    }

    private fun buildParagraphs(chunks: List<Chunk>, underlineCandidates: List<UnderlineCandidate>): List<ExtractedParagraph> {
        val lines = mutableListOf<LineAccumulator>()
        var current = LineAccumulator()

        fun flushLine() {
            if (current.runs.isNotEmpty()) {
                lines.add(current)
            }
            current = LineAccumulator()
        }

        for (chunk in chunks) {
            when (chunk) {
                is Chunk.LineBreak -> flushLine()
                is Chunk.Text -> {
                    current.runs.add(
                        ExtractedTextRun(
                            text = chunk.text,
                            isBold = chunk.fontName.contains("bold", ignoreCase = true),
                            isItalic = chunk.fontName.contains("italic", ignoreCase = true) ||
                                chunk.fontName.contains("oblique", ignoreCase = true),
                            isUnderline = false,
                            fontSize = chunk.fontSize,
                            fontFamily = cleanFontName(chunk.fontName),
                        ),
                    )
                    if (chunk.x < current.minX) current.minX = chunk.x
                    if (chunk.endX > current.maxX) current.maxX = chunk.endX
                    current.y = chunk.y
                }
            }
        }
        flushLine()

        if (lines.isEmpty()) {
            return listOf(ExtractedParagraph(runs = listOf(emptyRun()), alignment = 0))
        }

        // Ampirik "sayfa kenar boşluğu": belgedeki tüm satırların gözlemlenen en sol/en sağ
        // uçları — gerçek sayfa genişliğinden daha güvenilir bir referans (belgenin kendi
        // marjlarını yansıtır).
        val globalLeft = lines.minOf { it.minX }
        val globalRight = lines.maxOf { it.maxX }
        val tolerance = 10f

        return lines.map { line ->
            val underlineDetected = underlineCandidates.any { rect ->
                val xOverlap = rect.xMax >= line.minX + 2f && rect.xMin <= line.maxX - 2f
                val yMatch = rect.yTop <= line.y + 14f && rect.yBottom >= line.y - 2f
                xOverlap && yMatch
            }
            val runs = if (underlineDetected) line.runs.map { it.copy(isUnderline = true) } else line.runs

            val leftClose = line.minX <= globalLeft + tolerance
            val rightClose = line.maxX >= globalRight - tolerance
            val alignment = when {
                leftClose && rightClose -> 3 // tam genişlik — iki yana yasla (veya doğal dolu tek satır)
                leftClose -> 0
                rightClose -> 2
                else -> {
                    val leftGap = line.minX - globalLeft
                    val rightGap = globalRight - line.maxX
                    if (kotlin.math.abs(leftGap - rightGap) <= tolerance * 2) 1 else 0
                }
            }

            val merged = mergeRuns(runs)
            ExtractedParagraph(runs = if (merged.isEmpty()) listOf(emptyRun()) else merged, alignment = alignment)
        }
    }

    private fun emptyRun() = ExtractedTextRun(
        text = "",
        isBold = false,
        isItalic = false,
        isUnderline = false,
        fontSize = 12f,
        fontFamily = "Times New Roman",
    )

    /** "ABCDEF+Arial-BoldMT" biçimindeki alt küme önekini ("ABCDEF+") kaldırır. */
    private fun cleanFontName(fontName: String): String {
        val afterSubset = fontName.substringAfter('+', fontName)
        return afterSubset.ifEmpty { "Times New Roman" }
    }

    /** Aynı biçimlendirmeye sahip ardışık run'ları birleştirir (gürültüyü azaltmak için). */
    private fun mergeRuns(runs: List<ExtractedTextRun>): List<ExtractedTextRun> {
        if (runs.isEmpty()) return runs
        val result = mutableListOf<ExtractedTextRun>()
        var current = runs[0]

        for (i in 1 until runs.size) {
            val next = runs[i]
            current = if (
                current.isBold == next.isBold &&
                current.isItalic == next.isItalic &&
                current.isUnderline == next.isUnderline &&
                current.fontSize == next.fontSize &&
                current.fontFamily == next.fontFamily
            ) {
                current.copy(text = current.text + next.text)
            } else {
                result.add(current)
                next
            }
        }
        result.add(current)
        return result
    }
}
