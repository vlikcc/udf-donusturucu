package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
import com.velikececi.udfdonusturucu.core.model.ExtractedContent
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.ExtractionException
import java.io.File
import java.io.IOException

/**
 * PDFExtractor.swift'in Kotlin karşılığı. iOS tarafı PDFKit'in `page.attributedString`
 * soyutlamasını kullanıyor (font/hizalama bilgisini hazır veriyor); Android'de bunun bir
 * eşdeğeri yok, bu yüzden PdfBox-Android'in `PDFTextStripper`ı alt sınıflanarak kelime bazlı
 * `TextPosition`lardan font adı/boyutu okunur.
 *
 * Bilinen platform farkları (metnin kendisini etkilemez, yalnızca biçimlendirme çıkarımını):
 * - **Hizalama** PDF içerik akışından güvenilir biçimde çıkarılamaz (PDF'de anlamsal paragraf
 *   hizalaması saklanmaz) — tüm çıkarılan paragraflar `alignment = 0` (sol) ile döner.
 * - **Altı çizili** metin PDF'de ayrı bir vektör çizgisi olarak çizilir, bir metin özniteliği
 *   değildir; PdfBox bunu tespit etmez — `isUnderline` her zaman false döner.
 * - Kalın/italik ise PDF fontunun temel adından (`Bold`/`Italic`/`Oblique` alt dizisi) çıkarılır.
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
            val stripper = ChunkCollectingStripper()
            stripper.sortByPosition = true
            stripper.getText(document)

            val paragraphs = buildParagraphs(stripper.chunks)
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

    // MARK: - Styled chunk collection

    private sealed class Chunk {
        data class Text(val text: String, val fontName: String, val fontSize: Float) : Chunk()
        object LineBreak : Chunk()
    }

    private class ChunkCollectingStripper : PDFTextStripper() {
        val chunks = mutableListOf<Chunk>()

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            if (text.isEmpty()) return
            val first = textPositions.firstOrNull()
            val fontName = first?.font?.name ?: ""
            val fontSize = first?.fontSizeInPt ?: 12f
            chunks.add(Chunk.Text(text, fontName, fontSize))
        }

        override fun writeWordSeparator() {
            val lastText = chunks.lastOrNull { it is Chunk.Text } as? Chunk.Text
            chunks.add(Chunk.Text(" ", lastText?.fontName ?: "", lastText?.fontSize ?: 12f))
        }

        override fun writeLineSeparator() {
            chunks.add(Chunk.LineBreak)
        }
    }

    // MARK: - Paragraph + Run building

    private fun buildParagraphs(chunks: List<Chunk>): List<ExtractedParagraph> {
        val paragraphs = mutableListOf<ExtractedParagraph>()
        var currentRuns = mutableListOf<ExtractedTextRun>()

        fun flush() {
            val merged = mergeRuns(currentRuns)
            paragraphs.add(
                ExtractedParagraph(
                    runs = if (merged.isEmpty()) listOf(emptyRun()) else merged,
                    alignment = 0,
                ),
            )
            currentRuns = mutableListOf()
        }

        for (chunk in chunks) {
            when (chunk) {
                is Chunk.LineBreak -> flush()
                is Chunk.Text -> currentRuns.add(
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
            }
        }
        if (currentRuns.isNotEmpty()) flush()

        if (paragraphs.isEmpty()) {
            paragraphs.add(ExtractedParagraph(runs = listOf(emptyRun()), alignment = 0))
        }

        return paragraphs
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
