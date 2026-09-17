package com.velikececi.udfdonusturucu.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * NOT: `sample_uyap.udf`/`sample_plain_text.udf` gibi mevcut fikstürlerin hiçbirinde gerçek bir
 * imza (P7S/sertifika) yok — bu yüzden burada yalnızca "imza bulunamadı" ve hata yolları
 * doğrulanıyor. Sertifika/imza-zamanı ayrıştırmasının pozitif yolu (DER tarama, CN çıkarımı,
 * UTCTime/GeneralizedTime ayrıştırma) gerçek bir X.509 sertifikası gömülü `sample_signed.udf`
 * fikstürü gerektirir; bu fikstür henüz üretilmedi.
 */
class SignatureInspectorTest {

    private fun fixture(name: String): File {
        val url = requireNotNull(javaClass.classLoader?.getResource(name)) { "Fikstür bulunamadı: $name" }
        return File(url.toURI())
    }

    @Test
    fun `imzasiz UYAP dosyasinda imza bulunmaz`() {
        val result = SignatureInspector.inspect(fixture("sample_uyap.udf"))

        assertFalse(result.hasSignature)
        assertTrue(result.certificates.isEmpty())
        assertTrue(result.signingDates.isEmpty())
    }

    @Test
    fun `imzasiz duz metin dosyasinda imza bulunmaz`() {
        val result = SignatureInspector.inspect(fixture("sample_plain_text.udf"))

        assertFalse(result.hasSignature)
    }

    @Test
    fun `bozuk zip CannotRead firlatir`() {
        assertThrows(SignatureInspectionException.CannotRead::class.java) {
            SignatureInspector.inspect(fixture("corrupted.udf"))
        }
    }
}
