package com.velikececi.udfdonusturucu.core.model

/**
 * PDFExtractor.swift / DOCXExtractor.swift'teki `ExtractedContent`/`ExtractedParagraph`/`TextRun`
 * yapılarının ortak Kotlin karşılığı — hem PdfExtractor hem DocxExtractor bu modele dönüştürür,
 * UdfCreator da yalnızca bu modeli tüketir (iOS'taki `UDFCreator.InputParagraph`/`InputRun` ile
 * aynı rolü görür).
 */
data class ExtractedTextRun(
    val text: String,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val fontSize: Float,
    val fontFamily: String,
)

data class ExtractedParagraph(
    val runs: List<ExtractedTextRun>,
    val alignment: Int, // 0=sol, 1=orta, 2=sağ, 3=iki yana yasla
) {
    val text: String get() = runs.joinToString("") { it.text }
}

data class ExtractedContent(
    val plainText: String,
    val paragraphs: List<ExtractedParagraph>,
)

sealed class ExtractionException(message: String) : Exception(message) {
    class CannotOpenFile : ExtractionException("Dosya açılamadı.")
    class NoTextContent : ExtractionException("Dosyada metin içeriği bulunamadı.")
    class InvalidFormat : ExtractionException("Geçersiz dosya formatı.")
}

sealed class ConversionException(message: String) : Exception(message) {
    class PdfCreationFailed : ConversionException("PDF oluşturulamadı.")
    class DocxCreationFailed : ConversionException("DOCX oluşturulamadı.")
    class ExportFailed(detail: String) : ConversionException("Dosya kaydetme hatası: $detail")
}
