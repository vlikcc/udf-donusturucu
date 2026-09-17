package com.velikececi.udfdonusturucu.ui.preview

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.UdfDocument

/**
 * UDF ve DOCX önizlemeleri için ortak biçimli metin kurucusu. Her iki kaynak da
 * "paragraf + run" modeline indirgenir ([ExtractedParagraph]); kalın/italik/altı çizili
 * ve paragraf hizalaması korunur. iOS'taki `DocumentPreviewView` AttributedString
 * kurulumunun karşılığıdır.
 */
fun buildAnnotatedPreviewText(paragraphs: List<ExtractedParagraph>): AnnotatedString =
    buildAnnotatedString {
        paragraphs.forEachIndexed { index, paragraph ->
            val paraStart = length
            for (run in paragraph.runs) {
                val runStart = length
                append(run.text)
                val style = SpanStyle(
                    fontWeight = if (run.isBold) FontWeight.Bold else null,
                    fontStyle = if (run.isItalic) FontStyle.Italic else null,
                    textDecoration = if (run.isUnderline) TextDecoration.Underline else null,
                )
                if (run.isBold || run.isItalic || run.isUnderline) {
                    addStyle(style, runStart, length)
                }
            }
            val textAlign = when (paragraph.alignment) {
                1 -> TextAlign.Center
                2 -> TextAlign.Right
                3 -> TextAlign.Justify
                else -> TextAlign.Start
            }
            if (textAlign != TextAlign.Start && length > paraStart) {
                addStyle(ParagraphStyle(textAlign = textAlign), paraStart, length)
            }
            if (index < paragraphs.size - 1) append("\n")
        }
    }

/**
 * UYAP offset-tabanlı paragraf/run modelini ortak [ExtractedParagraph] modeline çevirir.
 * Paragraf yoksa (düz metin UDF) tüm metin tek paragraf olarak döner.
 */
fun UdfDocument.toExtractedParagraphs(): List<ExtractedParagraph> {
    val text = content.text
    if (content.paragraphs.isEmpty()) {
        return listOf(
            ExtractedParagraph(
                runs = listOf(ExtractedTextRun(text, isBold = false, isItalic = false, isUnderline = false, fontSize = 12f, fontFamily = "")),
                alignment = 0,
            ),
        )
    }

    return content.paragraphs.mapNotNull { paragraph ->
        val runs = paragraph.runs.mapNotNull { run ->
            val start = run.startOffset.coerceIn(0, text.length)
            val end = (run.startOffset + run.length).coerceIn(start, text.length)
            if (end <= start) return@mapNotNull null
            ExtractedTextRun(
                text = text.substring(start, end),
                isBold = run.bold,
                isItalic = run.italic,
                isUnderline = run.underline,
                fontSize = run.fontSize ?: 12f,
                fontFamily = run.fontFamily.orEmpty(),
            )
        }
        if (runs.isEmpty()) null else ExtractedParagraph(runs = runs, alignment = paragraph.alignment)
    }
}
