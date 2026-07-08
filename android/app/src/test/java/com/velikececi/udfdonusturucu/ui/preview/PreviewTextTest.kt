package com.velikececi.udfdonusturucu.ui.preview

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreviewTextTest {

    private fun run(text: String, bold: Boolean = false, italic: Boolean = false, underline: Boolean = false) =
        ExtractedTextRun(text, isBold = bold, isItalic = italic, isUnderline = underline, fontSize = 12f, fontFamily = "")

    @Test
    fun `paragraflar yeni satirla birlesir ve kalin run dogru araliga uygulanir`() {
        val result = buildAnnotatedPreviewText(
            listOf(
                ExtractedParagraph(runs = listOf(run("Başlık", bold = true)), alignment = 1),
                ExtractedParagraph(runs = listOf(run("Normal "), run("altı çizili", underline = true)), alignment = 0),
            ),
        )

        assertEquals("Başlık\nNormal altı çizili", result.text)

        val boldSpans = result.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(1, boldSpans.size)
        assertEquals(0, boldSpans[0].start)
        assertEquals("Başlık".length, boldSpans[0].end)

        val centered = result.paragraphStyles.filter { it.item.textAlign == TextAlign.Center }
        assertEquals(1, centered.size)
        assertEquals(0, centered[0].start)
        assertEquals("Başlık".length, centered[0].end)
    }

    @Test
    fun `UYAP belgesi ortak paragraf modeline offsetler korunarak cevrilir`() {
        val url = requireNotNull(javaClass.classLoader?.getResource("sample_uyap.udf"))
        val doc = UdfParser.parse(File(url.toURI()))

        val paragraphs = doc.toExtractedParagraphs()
        assertEquals(doc.content.paragraphs.size, paragraphs.size)
        // Çevrilen paragraf metinlerinin birleşimi orijinal düz metinle eşleşmeli.
        assertEquals(doc.content.text, paragraphs.joinToString("\n") { it.text })
        assertTrue(paragraphs.all { it.runs.isNotEmpty() })
    }
}
