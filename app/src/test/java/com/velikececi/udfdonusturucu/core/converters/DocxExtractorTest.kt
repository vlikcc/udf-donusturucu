package com.velikececi.udfdonusturucu.core.converters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fikstür `sample.docx`, `build_docx_fixture.py` ile üretildi. DocxExtractor saf JVM kodu
 * olduğundan (android.* bağımlılığı yok) bu test Robolectric gerektirmeden çalışır — ancak
 * Faz 3'ün geri kalanı gibi (PdfExtractor, PdfConverter, WordConverter, UdfCreator) bu ortamda
 * JDK 17 olmadığı için ÇALIŞTIRILAMADI, yalnızca statik olarak gözden geçirildi.
 */
class DocxExtractorTest {

    private fun fixture(name: String): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(name)) { "Fikstür bulunamadı: $name" }
        return File(url.toURI())
    }

    @Test
    fun `DOCX paragraflari hizalama ve run formatlamasiyla dogru cikarilir`() {
        val extracted = DocxExtractor.extract(fixture("sample.docx"))

        assertEquals(3, extracted.paragraphs.size)
        assertEquals(
            "Dava Dilekçesi\nSayın Hakimliğe, acilen talep ediyoruz.\nİkinci paragraf burada.",
            extracted.plainText,
        )

        val p1 = extracted.paragraphs[0]
        assertEquals(1, p1.alignment) // center
        assertEquals(1, p1.runs.size)
        assertTrue(p1.runs[0].isBold)
        assertEquals(12.0f, p1.runs[0].fontSize)
        assertEquals("Calibri", p1.runs[0].fontFamily)

        val p2 = extracted.paragraphs[1]
        assertEquals(0, p2.alignment)
        assertEquals(3, p2.runs.size)
        assertEquals("Sayın Hakimliğe, ", p2.runs[0].text)
        assertEquals("acilen", p2.runs[1].text)
        assertTrue(p2.runs[1].isItalic)
        assertTrue(p2.runs[1].isUnderline)
        assertEquals(14.0f, p2.runs[1].fontSize)
        assertEquals(" talep ediyoruz.", p2.runs[2].text)

        val p3 = extracted.paragraphs[2]
        assertEquals(3, p3.alignment) // both -> justify
        assertEquals("İkinci paragraf burada.", p3.text)
    }
}
