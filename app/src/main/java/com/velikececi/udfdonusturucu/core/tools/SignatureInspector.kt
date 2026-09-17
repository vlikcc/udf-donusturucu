package com.velikececi.udfdonusturucu.core.tools

import com.velikececi.udfdonusturucu.core.zip.UdfZip
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class SignatureCertificate(val id: String = UUID.randomUUID().toString(), val subjectSummary: String)

data class SignatureInspectionResult(
    val signatureEntryNames: List<String>,
    val certificates: List<SignatureCertificate>,
    val signingDates: List<Date>,
    val hasSignature: Boolean,
)

sealed class SignatureInspectionException(message: String) : Exception(message) {
    class CannotRead : SignatureInspectionException("Dosya okunamadı.")
}

/**
 * SignatureInspector.swift'in Kotlin karşılığı. UDF ZIP'i içindeki imza dosyalarını, bir
 * XML/ASN.1 ayrıştırıcısına ihtiyaç duymadan byte-scan ile tarar:
 * - **Sertifika**: DER SEQUENCE deseni (`30 82 <uzunluk_hi> <uzunluk_lo>`, uzunluk > 200) →
 *   [CertificateFactory] ile X.509 olarak ayrıştırma denemesi.
 * - **İmza zamanı**: signingTime OID (`06 09 2A 86 48 86 F7 0D 01 09 05`) sonrası 8 bayt içinde
 *   UTCTime (`0x17`, `yyMMddHHmmss'Z'`) veya GeneralizedTime (`0x18`, `yyyyMMddHHmmss'Z'`) etiketi.
 *
 * iOS'taki `count - 4` sınır kontrolü sınırda bir baytlık taşma riski taşıyordu; burada
 * `i + 3 < bytes.size` (DER) / `i + oidLength <= bytes.size` (OID) kullanılarak düzeltilmiştir.
 *
 * SALT BİLGİ AMAÇLIDIR — imzanın hukuki geçerliliğini veya sertifika zincirini doğrulamaz.
 */
object SignatureInspector {

    private val SIGNATURE_NAME_HINTS = listOf("imza", "sign", ".p7s", ".sig", ".pkcs")
    private val METADATA_ENTRY_NAMES = setOf("content.xml", "documentproperties.xml", "properties.xml")

    // OID 1.2.840.113549.1.9.5 (signingTime) — DER kodlanmış hali.
    private val SIGNING_TIME_OID = byteArrayOf(
        0x06, 0x09,
        0x2A.toByte(), 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x09, 0x05,
    )

    private val certificateFactory: CertificateFactory by lazy { CertificateFactory.getInstance("X.509") }

    fun inspect(file: File): SignatureInspectionResult {
        val entries = try {
            UdfZip.extractEntries(file)
        } catch (e: Exception) {
            throw SignatureInspectionException.CannotRead()
        }

        var candidates = entries.filter { entry ->
            val name = entry.fileName.lowercase(Locale.ROOT)
            SIGNATURE_NAME_HINTS.any { hint -> name.contains(hint) }
        }
        if (candidates.isEmpty()) {
            candidates = entries.filter { entry ->
                entry.fileName.lowercase(Locale.ROOT) !in METADATA_ENTRY_NAMES &&
                    containsDerCertificateCandidate(entry.data)
            }
        }

        val certificates = mutableListOf<SignatureCertificate>()
        val seenSubjects = mutableSetOf<String>()
        val signingDates = mutableListOf<Date>()

        for (entry in candidates) {
            extractCertificates(entry.data).forEach { cert ->
                val summary = subjectSummary(cert)
                if (seenSubjects.add(summary)) {
                    certificates.add(SignatureCertificate(subjectSummary = summary))
                }
            }
            signingDates += extractSigningTimes(entry.data)
        }

        return SignatureInspectionResult(
            signatureEntryNames = candidates.map { it.fileName },
            certificates = certificates,
            signingDates = signingDates.sorted(),
            hasSignature = candidates.isNotEmpty(),
        )
    }

    // MARK: - DER sertifika taraması

    private fun containsDerCertificateCandidate(bytes: ByteArray): Boolean {
        var i = 0
        while (i + 3 < bytes.size) {
            if (bytes[i] == 0x30.toByte() && bytes[i + 1] == 0x82.toByte()) {
                val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                if (length > 200) return true
            }
            i++
        }
        return false
    }

    private fun extractCertificates(bytes: ByteArray): List<X509Certificate> {
        val results = mutableListOf<X509Certificate>()
        var i = 0
        while (i + 3 < bytes.size) {
            if (bytes[i] == 0x30.toByte() && bytes[i + 1] == 0x82.toByte()) {
                val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
                val totalLength = length + 4
                if (length > 200 && totalLength <= 65536 && i + totalLength <= bytes.size) {
                    val candidate = bytes.copyOfRange(i, i + totalLength)
                    try {
                        val cert = certificateFactory.generateCertificate(ByteArrayInputStream(candidate)) as? X509Certificate
                        if (cert != null) {
                            results.add(cert)
                            i += totalLength
                            continue
                        }
                    } catch (e: CertificateException) {
                        // Aday geçersiz bir sertifika değildi — bir sonraki bayttan taramaya devam et.
                    }
                }
            }
            i++
        }
        return results
    }

    // MARK: - İmza zamanı taraması

    private fun extractSigningTimes(bytes: ByteArray): List<Date> {
        val results = mutableListOf<Date>()
        val oidLength = SIGNING_TIME_OID.size
        var i = 0
        while (i + oidLength <= bytes.size) {
            if (regionMatches(bytes, i, SIGNING_TIME_OID)) {
                val searchStart = i + oidLength
                val searchEnd = minOf(searchStart + 8, bytes.size)
                var j = searchStart
                while (j < searchEnd) {
                    val tag = bytes[j].toInt() and 0xFF
                    if (tag == 0x17 || tag == 0x18) {
                        if (j + 1 < bytes.size) {
                            val length = bytes[j + 1].toInt() and 0xFF
                            if (length in 1..31) {
                                val start = j + 2
                                val end = start + length
                                if (end <= bytes.size) {
                                    val raw = String(bytes, start, length, Charsets.US_ASCII)
                                    parseSigningTime(raw, isGeneralized = tag == 0x18)?.let { results.add(it) }
                                }
                            }
                        }
                        break
                    }
                    j++
                }
                i = searchStart
            } else {
                i++
            }
        }
        return results
    }

    private fun regionMatches(bytes: ByteArray, offset: Int, pattern: ByteArray): Boolean {
        if (offset + pattern.size > bytes.size) return false
        for (k in pattern.indices) {
            if (bytes[offset + k] != pattern[k]) return false
        }
        return true
    }

    /** UTCTime iki haneli yıl kullanır — `SimpleDateFormat`'ın varsayılan pivotu iOS'un iki haneli yıl davranışıyla eşleşir. */
    private fun parseSigningTime(raw: String, isGeneralized: Boolean): Date? {
        val pattern = if (isGeneralized) "yyyyMMddHHmmss'Z'" else "yyMMddHHmmss'Z'"
        return try {
            SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(raw)
        } catch (e: Exception) {
            null
        }
    }

    // MARK: - Konu özeti (CN çıkarımı)

    /** iOS `SecCertificateCopySubjectSummary` yaklaşık karşılığı — CN varsa onu, yoksa tam DN'yi döner. */
    private fun subjectSummary(cert: X509Certificate): String {
        val dn = cert.subjectX500Principal.name
        return extractCommonName(dn) ?: dn
    }

    /**
     * RFC2253 DN'sinden `CN=...` bileşenini çıkarır. `javax.naming.ldap.LdapName` Android'de
     * yoktur; bu yüzden `\,` kaçış dizisine dikkat eden elle yazılmış küçük bir ayraç kullanılır.
     */
    private fun extractCommonName(dn: String): String? {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < dn.length) {
            val c = dn[i]
            if (c == '\\' && i + 1 < dn.length) {
                current.append(c).append(dn[i + 1])
                i += 2
                continue
            }
            if (c == ',') {
                parts.add(current.toString())
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) parts.add(current.toString())

        val cnPart = parts.map { it.trim() }.firstOrNull { it.startsWith("CN=", ignoreCase = true) } ?: return null
        return cnPart.substring(3).replace("\\,", ",").trim().takeIf { it.isNotEmpty() }
    }
}
