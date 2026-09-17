package com.velikececi.udfdonusturucu.converter

enum class ConversionDirection(val title: String, val description: String) {
    UDF_TO_PDF("UDF -> PDF", "UYAP UDF belgesini PDF formatına dönüştürün"),
    UDF_TO_DOCX("UDF -> Word (DOCX)", "UYAP UDF belgesini Microsoft Word formatına dönüştürün"),
    PDF_TO_UDF("PDF -> UDF", "PDF belgesini UYAP uyumlu UDF formatına dönüştürün"),
    DOCX_TO_UDF("Word (DOCX) -> UDF", "Microsoft Word belgesini UYAP uyumlu UDF formatına dönüştürün")
}
