package com.velikececi.udfdonusturucu.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {

    private val template = TemplateLibrary.all.first { it.id == "genel-dilekce" }

    @Test
    fun `bos alan noktali cizgiyle doldurulur`() {
        val text = TemplateEngine.fill(template, emptyMap())

        assertTrue(text.contains("KONU: ................"))
        assertFalse(text.contains("{{konu}}"))
    }

    @Test
    fun `doldurulmus alan degeriyle yer degistirir`() {
        val text = TemplateEngine.fill(
            template,
            mapOf("makam" to "ANKARA VALİLİĞİNE", "konu" to "İzin talebi", "aciklama" to "Açıklama metni"),
        )

        assertTrue(text.contains("ANKARA VALİLİĞİNE"))
        assertTrue(text.contains("KONU: İzin talebi"))
        assertTrue(text.contains("Açıklama metni"))
    }

    @Test
    fun `bosluklar kirpilir`() {
        val text = TemplateEngine.fill(template, mapOf("konu" to "   Kenar boşluklu   "))

        assertTrue(text.contains("KONU: Kenar boşluklu"))
    }

    @Test
    fun `tamami buyuk harf kisa satir baslik sayilir ve ortalanir`() {
        val paragraphs = TemplateEngine.makeParagraphs("İSTANBUL 3. ASLİYE HUKUK MAHKEMESİNE")

        assertEquals(1, paragraphs.size)
        assertEquals(1, paragraphs[0].alignment)
        assertTrue(paragraphs[0].runs.first().isBold)
    }

    @Test
    fun `rakamdan olusan satir baslik sayilmaz`() {
        // Türkçe dotless-i tuzağı: sadece rakam içeren bir satırda büyük harf YOKTUR,
        // bu yüzden başlık sezgisi (en az bir büyük harf şartı) devre dışı kalmalı.
        val paragraphs = TemplateEngine.makeParagraphs("1234")

        assertEquals(3, paragraphs[0].alignment)
        assertFalse(paragraphs[0].runs.first().isBold)
    }

    @Test
    fun `normal cumle iki yana yaslanir`() {
        val paragraphs = TemplateEngine.makeParagraphs("Bu, normal uzunlukta bir cümledir ve başlık değildir.")

        assertEquals(3, paragraphs[0].alignment)
        assertFalse(paragraphs[0].runs.first().isBold)
    }

    @Test
    fun `80 karakterden uzun buyuk harf satir baslik sayilmaz`() {
        val longUppercase = "A".repeat(85)
        val paragraphs = TemplateEngine.makeParagraphs(longUppercase)

        assertEquals(3, paragraphs[0].alignment)
        assertFalse(paragraphs[0].runs.first().isBold)
    }

    @Test
    fun `6 sablon da benzersiz kimlige sahiptir`() {
        val ids = TemplateLibrary.all.map { it.id }
        assertEquals(6, ids.size)
        assertEquals(ids.toSet().size, ids.size)
    }
}
