package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import com.velikececi.udfdonusturucu.core.converters.PdfConverter
import com.velikececi.udfdonusturucu.core.converters.UdfCreator
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.UdfContent
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.model.UdfSection
import java.io.File
import java.util.Locale

/** TemplateEngine.swift'in Kotlin karşılığı — şablon doldurma + UDF/PDF üretimi. */
object TemplateEngine {

    fun fill(template: PetitionTemplate, values: Map<String, String>): String {
        var text = template.body
        for (field in template.fields) {
            val value = values[field.key]?.trim().orEmpty()
            // Boş alan, iOS ile aynı şekilde noktalı boşluk çizgisiyle doldurulur.
            text = text.replace("{{${field.key}}}", value.ifEmpty { "................" })
        }
        return text
    }

    /**
     * Satır bazlı paragraf üretir: tamamı BÜYÜK HARF olan kısa satırlar ortalanır ve kalın
     * yazılır (mahkeme başlığı, "DOSYA NO:" vb.), diğer satırlar iki yana yaslanır.
     */
    fun makeParagraphs(text: String): List<ExtractedParagraph> =
        text.split("\n").map { line ->
            val trimmed = line.trim()
            val isHeading = trimmed.isNotEmpty() &&
                trimmed.length < 80 &&
                trimmed == trimmed.uppercase(Locale("tr", "TR")) &&
                trimmed.any { it.isUpperCase() }

            ExtractedParagraph(
                runs = listOf(
                    ExtractedTextRun(
                        text = line,
                        isBold = isHeading,
                        isItalic = false,
                        isUnderline = false,
                        fontSize = 12f,
                        fontFamily = "Times New Roman",
                    ),
                ),
                alignment = if (isHeading) 1 else 3,
            )
        }

    fun createUdf(template: PetitionTemplate, values: Map<String, String>, context: Context): File {
        val paragraphs = makeParagraphs(fill(template, values))
        return UdfCreator.create(template.title, paragraphs, context)
    }

    fun createPdf(template: PetitionTemplate, values: Map<String, String>, context: Context): File {
        val text = fill(template, values)
        val document = UdfDocument(
            fileName = template.title,
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
        return PdfConverter.convert(document, context)
    }
}
