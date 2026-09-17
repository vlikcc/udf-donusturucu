package com.velikececi.udfdonusturucu.ui.tools.editor.richtext

import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditParagraph
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditRun
import com.velikececi.udfdonusturucu.core.tools.editor.UdfRunKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `android.text.Spannable`/`SpannableStringBuilder` gerçek span izleme mantığına sahiptir (salt
 * stub değil) — bu yüzden Robolectric gerektirir (diğer testlerin aksine, bkz. PreviewTextTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UdfSpanCodecTest {

    @Test
    fun `kalin italik alti cizili model Spannable'a ve geri modele dogru donusur`() {
        val paragraph = UdfEditParagraph(
            runs = listOf(
                UdfEditRun(text = "Kalın ", isBold = true),
                UdfEditRun(text = "italik ", isItalic = true),
                UdfEditRun(text = "altı çizili", isUnderline = true),
            ),
        )

        val spannable = UdfSpanCodec.toSpannable(paragraph)
        assertEquals("Kalın italik altı çizili", spannable.toString())

        val roundTripped = UdfSpanCodec.toParagraph(spannable, paragraph)

        assertEquals(3, roundTripped.runs.size)
        assertEquals("Kalın ", roundTripped.runs[0].text)
        assertTrue(roundTripped.runs[0].isBold)
        assertEquals("italik ", roundTripped.runs[1].text)
        assertTrue(roundTripped.runs[1].isItalic)
        assertEquals("altı çizili", roundTripped.runs[2].text)
        assertTrue(roundTripped.runs[2].isUnderline)
    }

    @Test
    fun `argb renk kimlik donusumuyle korunur`() {
        val blackArgb = -16777216 // Color.BLACK
        val paragraph = UdfEditParagraph(runs = listOf(UdfEditRun(text = "renkli", foregroundARGB = blackArgb, backgroundARGB = 0x66FFEB3B.toInt())))

        val roundTripped = UdfSpanCodec.toParagraph(UdfSpanCodec.toSpannable(paragraph), paragraph)

        assertEquals(blackArgb, roundTripped.runs.first().foregroundARGB)
        assertEquals(0x66FFEB3B.toInt(), roundTripped.runs.first().backgroundARGB)
    }

    @Test
    fun `yazi tipi ailesi TypefaceSpan'dan degil modelden korunur`() {
        // "Times New Roman" render için "serif"e eşlenir ama orijinal ad FontFamilySpan ile taşınır.
        val paragraph = UdfEditParagraph(runs = listOf(UdfEditRun(text = "metin", fontFamily = "Times New Roman")))

        val roundTripped = UdfSpanCodec.toParagraph(UdfSpanCodec.toSpannable(paragraph), paragraph)

        assertEquals("Times New Roman", roundTripped.runs.first().fontFamily)
    }

    @Test
    fun `alan calismasi field turunde ve vurgusuz geri doner`() {
        val paragraph = UdfEditParagraph(
            runs = listOf(UdfEditRun(kind = UdfRunKind.Field("TARIH"), text = "01.01.2026")),
        )

        val roundTripped = UdfSpanCodec.toParagraph(UdfSpanCodec.toSpannable(paragraph), paragraph)

        val run = roundTripped.runs.first()
        assertTrue(run.isField)
        assertEquals("TARIH", run.fieldName)
        assertEquals("01.01.2026", run.text)
        // Alan vurgusu (BackgroundColorSpan) kendi başına bir "background" değeri olarak geri okunmaz.
        assertEquals(null, run.backgroundARGB)
    }

    @Test
    fun `bos paragraf tek bos run doner`() {
        val paragraph = UdfEditParagraph(runs = emptyList())

        val roundTripped = UdfSpanCodec.toParagraph(UdfSpanCodec.toSpannable(paragraph), paragraph)

        assertEquals(1, roundTripped.runs.size)
        assertEquals("", roundTripped.runs.first().text)
    }
}
