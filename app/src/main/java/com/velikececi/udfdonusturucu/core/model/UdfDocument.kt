package com.velikececi.udfdonusturucu.core.model

/**
 * UDFDocument.swift'in Kotlin karşılığı — UYAP .udf belgesinin ayrıştırılmış modeli.
 */
data class UdfDocument(
    val fileName: String,
    val content: UdfContent,
    val metadata: UdfMetadata?,
    val pageFormat: UdfPageFormat?,
)

data class UdfPageFormat(
    val leftMargin: Float,
    val rightMargin: Float,
    val topMargin: Float,
    val bottomMargin: Float,
) {
    companion object {
        /** UYAP varsayılanı: A4, 2cm/1cm kenar boşlukları (1/72" birim, iOS ile aynı). */
        val DEFAULT = UdfPageFormat(
            leftMargin = 56.69f,
            rightMargin = 56.69f,
            topMargin = 28.35f,
            bottomMargin = 28.35f,
        )
    }
}

enum class UdfContentType {
    UYAP,       // UYAP özel formatı: CDATA + <elements>
    HTML,
    RTF,
    PLAIN_TEXT,
}

data class UdfContent(
    val text: String,                         // CDATA içindeki düz metin
    val rawContent: String,                    // Orijinal XML/HTML/RTF
    val contentType: UdfContentType,
    val sections: List<UdfSection>,
    val tables: List<UdfTable>,
    val isRtf: Boolean,
    val paragraphs: List<UyapParagraph>,        // UYAP için önceden kurulmuş biçimlendirilmiş paragraflar
)

// MARK: - UYAP Paragraf Modeli

enum class UyapAlignment(val rawValue: Int) {
    LEFT(0),
    CENTER(1),
    RIGHT(2),
    JUSTIFY(3),
    ;

    companion object {
        fun fromRaw(value: Int): UyapAlignment = entries.firstOrNull { it.rawValue == value } ?: LEFT
    }
}

data class UyapParagraph(
    val alignment: Int,          // 0=sol, 1=orta, 3=iki yana yasla (UYAP ham değeri)
    val spaceAbove: Float,
    val spaceBelow: Float,
    val leftIndent: Float,
    val rightIndent: Float,
    val firstLineIndent: Float,
    val hangingIndent: Float,
    val lineSpacing: Float,
    val tabStops: List<Float>,
    val runs: List<UyapTextRun>,
)

data class UyapTextRun(
    val startOffset: Int,
    val length: Int,
    val bold: Boolean,
    val underline: Boolean,
    val italic: Boolean,
    val fontSize: Float?,
    val fontFamily: String?,
)

// MARK: - Section / Table / Metadata

data class UdfSection(
    val title: String?,
    val body: String,
    val level: Int,
)

data class UdfTable(
    val rows: List<List<String>>,
)

data class UdfMetadata(
    val author: String?,
    val creationDate: String?,
    val title: String?,
    val subject: String?,
)

sealed class UdfParserException(message: String) : Exception(message) {
    class FileNotFound : UdfParserException("UDF dosyası bulunamadı.")
    class InvalidZipArchive : UdfParserException("Geçersiz UDF arşiv formatı.")
    class NoContentFound : UdfParserException("UDF dosyasında içerik bulunamadı.")
    class ParsingFailed(detail: String) : UdfParserException("UDF parse hatası: $detail")
    class UnsupportedFormat : UdfParserException("Desteklenmeyen UDF formatı.")
}
