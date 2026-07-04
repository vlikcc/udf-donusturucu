package com.velikececi.udfdonusturucu.core.zip

import com.velikececi.udfdonusturucu.core.model.UdfParserException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UdfZipTest {

    private fun fixture(name: String): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(name)) { "Fikstür bulunamadı: $name" }
        return File(url.toURI())
    }

    @Test
    fun `content ve info girisleri dogru sirayla ve icerikle okunur`() {
        val entries = UdfZip.extractEntries(fixture("sample_uyap.udf"))

        assertEquals(2, entries.size)
        assertEquals("content.xml", entries[0].fileName)
        assertEquals("info.xml", entries[1].fileName)

        val contentText = String(entries[0].data, Charsets.UTF_8)
        assertTrue(contentText.contains("<template"))
        assertTrue(contentText.contains("<![CDATA["))
    }

    @Test
    fun `gecersiz zip InvalidZipArchive firlatir`() {
        assertThrows(UdfParserException.InvalidZipArchive::class.java) {
            UdfZip.extractEntries(fixture("corrupted.udf"))
        }
    }
}
