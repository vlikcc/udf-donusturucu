package com.velikececi.udfdonusturucu.core.parser

import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfParserException
import com.velikececi.udfdonusturucu.core.model.UyapTextRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun UyapTextRun.text(fullText: String): String = fullText.substring(startOffset, startOffset + length)

/**
 * Fikstürler `app/src/test/resources` altında `build_fixtures.py` ile üretildi (gerçek UYAP
 * sunucusundan alınmış bir .udf örneği bulunmadığı için, ayrıştırıcının beklediği format
 * kesin ofset/uzunluk değerleriyle sentetik olarak oluşturuldu). Bu proje ortamında JDK 17 /
 * Android Studio bulunmadığından bu test dosyası burada ÇALIŞTIRILAMADI — ilk doğrulama adımı
 * Android Studio'da `./gradlew testDebugUnitTest` olmalı.
 */
class UdfParserTest {

    private fun fixture(name: String): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(name)) { "Fikstür bulunamadı: $name" }
        return File(url.toURI())
    }

    @Test
    fun `UYAP belgesi duz metni ve paragraflari dogru ayristirir`() {
        val doc = UdfParser.parse(fixture("sample_uyap.udf"))

        assertEquals(UdfContentType.UYAP, doc.content.contentType)
        assertEquals(
            "Sayın Hâkimliğinize,\n" +
                "Davacı taraf, İstanbul Anadolu 3. Asliye Hukuk Mahkemesi'ne sunduğumuz dilekçemizde " +
                "belirttiğimiz üzere haklarımızın korunmasını talep etmekteyiz.\n" +
                "Saygılarımla, Av. Ayşe Çelik",
            doc.content.text,
        )

        val paragraphs = doc.content.paragraphs
        assertEquals(3, paragraphs.size)

        val p1 = paragraphs[0]
        assertEquals(0, p1.alignment)
        assertEquals(listOf(130.0f), p1.tabStops)
        assertEquals(1, p1.runs.size)
        assertEquals(0, p1.runs[0].startOffset)
        assertEquals(20, p1.runs[0].length)
        assertTrue(!p1.runs[0].bold)

        val p2 = paragraphs[1]
        assertEquals(3, p2.alignment)
        assertEquals(28.0f, p2.firstLineIndent)
        assertEquals(21, p2.runs[0].startOffset)
        assertEquals(146, p2.runs[0].length)

        val p3 = paragraphs[2]
        assertEquals(1, p3.alignment)
        assertEquals(2, p3.runs.size)
        assertEquals(168, p3.runs[0].startOffset)
        assertEquals(14, p3.runs[0].length)
        assertTrue(!p3.runs[0].bold)

        val boldRun = p3.runs[1]
        assertEquals(182, boldRun.startOffset)
        assertEquals(14, boldRun.length)
        assertTrue(boldRun.bold)
        assertTrue(boldRun.italic)
        assertTrue(boldRun.underline)
        assertEquals(14f, boldRun.fontSize)
        assertEquals("Arial", boldRun.fontFamily)
    }

    @Test
    fun `UYAP belgesinin sayfa marjlari ve metadata bilgisi okunur`() {
        val doc = UdfParser.parse(fixture("sample_uyap.udf"))

        val pageFormat = requireNotNull(doc.pageFormat)
        assertEquals(70.5f, pageFormat.leftMargin)
        assertEquals(70.5f, pageFormat.rightMargin)
        assertEquals(40.0f, pageFormat.topMargin)
        assertEquals(40.0f, pageFormat.bottomMargin)

        val metadata = requireNotNull(doc.metadata)
        assertEquals("Av. Ayşe Çelik", metadata.author)
        assertEquals("Dava Dilekçesi", metadata.title)
        assertEquals("2026-01-15", metadata.creationDate)
    }

    @Test
    fun `pageFormat elemani yoksa null doner`() {
        // sample_html_table.udf içeriğinde pageFormat elemanı yok — null dönmeli,
        // gerçek dönüştürme sırasında UdfPageFormat DEFAULT'a düşülür (Faz 3).
        val doc = UdfParser.parse(fixture("sample_html_table.udf"))
        assertNull(doc.pageFormat)
    }

    @Test
    fun `Windows-1254 kodlamali icerik Turkce karakterlerle dogru cozulur`() {
        val doc = UdfParser.parse(fixture("sample_uyap_windows1254.udf"))
        assertEquals("Şikâyetçi İğdır ili Öğüt köyünden gelmiştir.", doc.content.text)
    }

    @Test
    fun `HTML icerik tablo hucrelerini ve Turkce varliklari dogru cozer`() {
        val doc = UdfParser.parse(fixture("sample_html_table.udf"))

        assertEquals(UdfContentType.HTML, doc.content.contentType)
        assertTrue(doc.content.text.contains("Daçtör raporu aşağıdadır."))

        assertEquals(1, doc.content.tables.size)
        val rows = doc.content.tables[0].rows
        assertEquals(listOf(listOf("Ad", "Mehmet"), listOf("Soyad", "Yılmaz")), rows)
    }

    @Test
    fun `duz metinde BUYUK HARF ve iki nokta baslıklar bolum olarak ayrilir`() {
        val doc = UdfParser.parse(fixture("sample_plain_text.udf"))

        assertEquals(UdfContentType.PLAIN_TEXT, doc.content.contentType)
        val sections = doc.content.sections
        assertEquals(2, sections.size)
        assertEquals("GİRİŞ", sections[0].title)
        assertTrue(sections[0].body.contains("Bu davanın konusu şudur."))
        assertEquals("SONUÇ:", sections[1].title)
        assertEquals("Talep sonucu buradadır.", sections[1].body)
    }

    @Test
    fun `RTF icerik font tablosunu atlar ve kalin-italik-alti cizili run'lari dogru sinirlar`() {
        // Beklenen değerler algoritmanın Python simülasyonuyla (simulate_rtf.py) üretildi.
        val doc = UdfParser.parse(fixture("sample_rtf.udf"))

        assertEquals(UdfContentType.RTF, doc.content.contentType)
        assertEquals("Hello world plain.\nSecond italic and underlined end.\n", doc.content.text)

        val paragraphs = doc.content.paragraphs
        assertEquals(2, paragraphs.size)

        val p1 = paragraphs[0]
        assertEquals(3, p1.runs.size)
        assertEquals("Hello ", p1.runs[0].text(doc.content.text))
        assertTrue(!p1.runs[0].bold)
        assertEquals("world", p1.runs[1].text(doc.content.text))
        assertTrue(p1.runs[1].bold)
        assertEquals(" plain.", p1.runs[2].text(doc.content.text))
        assertTrue(!p1.runs[2].bold)

        val p2 = paragraphs[1]
        assertEquals(5, p2.runs.size)
        assertEquals("Second ", p2.runs[0].text(doc.content.text))
        assertEquals("italic", p2.runs[1].text(doc.content.text))
        assertTrue(p2.runs[1].italic)
        assertEquals(" and ", p2.runs[2].text(doc.content.text))
        assertEquals("underlined", p2.runs[3].text(doc.content.text))
        assertTrue(p2.runs[3].underline)
        assertEquals(" end.", p2.runs[4].text(doc.content.text))

        // Font tablosundaki "Times;" düz metne sızmamalı.
        assertTrue(!doc.content.text.contains("Times"))
    }

    @Test
    fun `bozuk zip dosyasi InvalidZipArchive firlatir`() {
        assertThrows(UdfParserException.InvalidZipArchive::class.java) {
            UdfParser.parse(fixture("corrupted.udf"))
        }
    }

    @Test
    fun `olmayan dosya FileNotFound firlatir`() {
        assertThrows(UdfParserException.FileNotFound::class.java) {
            UdfParser.parse(File("/bu/yol/olmayan/dosya.udf"))
        }
    }
}
