package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import com.velikececi.udfdonusturucu.core.model.ConversionException
import com.velikececi.udfdonusturucu.core.model.ExtractedContent
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.tools.editor.UdfEditDocument
import com.velikececi.udfdonusturucu.core.tools.editor.UdfStructureWriter
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * UDFCreator.swift'in Kotlin karşılığı — PDF/DOCX'ten çıkarılan içerikle UYAP-uyumlu .udf
 * dosyası oluşturur. iOS tarafı ZIP'i Compression çerçevesiyle elle (deflate + CRC32) inşa
 * etmek zorundaydı; Android'de `java.util.zip.ZipOutputStream` bunu otomatik yaptığından
 * içerik XML'i birebir aynı kalırken ZIP inşası çok daha basit.
 */
object UdfCreator {

    fun createFromPdf(file: File, context: Context): File {
        val extracted = PdfExtractor.extract(file, context)
        return buildUdf(fileName = file.nameWithoutExtension, extracted = extracted, context = context)
    }

    fun createFromDocx(file: File, context: Context): File {
        val extracted = DocxExtractor.extract(file)
        return buildUdf(fileName = file.nameWithoutExtension, extracted = extracted, context = context)
    }

    /**
     * iOS `UDFCreator.create(fileName:paragraphs:)` karşılığı — hazır paragraf listesinden
     * doğrudan .udf üretir. OCR dışa aktarımı (Faz 6) ve Dilekçe Şablonları ([TemplateEngine])
     * tarafından kullanılır.
     */
    fun create(fileName: String, paragraphs: List<ExtractedParagraph>, context: Context): File {
        val extracted = ExtractedContent(
            plainText = paragraphs.joinToString("\n") { it.text },
            paragraphs = paragraphs,
        )
        return buildUdf(fileName = fileName, extracted = extracted, context = context)
    }

    /**
     * iOS `UDFCreator.create(fileName:document:)` karşılığı — zengin düzenleme modelinden
     * (tablo, UYAP alanı, renk destekli) doğrudan .udf üretir. [com.velikececi.udfdonusturucu.core.tools.editor.UdfEditorService.save]
     * tarafından kullanılır.
     */
    fun create(fileName: String, document: UdfEditDocument, context: Context): File {
        val built = UdfStructureWriter.build(document)
        val contentXml = buildContentXmlFromElements(
            cdataText = built.cdataText,
            elementsXml = built.elementsXML,
            headersXml = built.headersXML,
            footersXml = built.footersXML,
        )
        val propertiesXml = buildPropertiesXml()
        val outputFile = OutputPaths.outputFile(context, fileName, "udf")

        try {
            ZipOutputStream(outputFile.outputStream()).use { zip ->
                writeEntry(zip, "content.xml", contentXml)
                writeEntry(zip, "documentproperties.xml", propertiesXml)
            }
        } catch (e: Exception) {
            throw ConversionException.ExportFailed(e.message ?: "bilinmeyen hata")
        }

        return outputFile
    }

    // MARK: - Build UDF ZIP

    private fun buildUdf(fileName: String, extracted: ExtractedContent, context: Context): File {
        val cdataText = extracted.paragraphs.joinToString("\n") { it.text }

        val contentXml = buildContentXml(cdataText, extracted.paragraphs)
        val propertiesXml = buildPropertiesXml()

        val outputFile = OutputPaths.outputFile(context, fileName, "udf")

        try {
            ZipOutputStream(outputFile.outputStream()).use { zip ->
                writeEntry(zip, "content.xml", contentXml)
                writeEntry(zip, "documentproperties.xml", propertiesXml)
            }
        } catch (e: Exception) {
            throw ConversionException.ExportFailed(e.message ?: "bilinmeyen hata")
        }

        return outputFile
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val entry = ZipEntry(name).apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    // MARK: - Content XML

    private fun buildContentXml(cdataText: String, paragraphs: List<ExtractedParagraph>): String {
        val elementsXml = StringBuilder("\n")
        var currentOffset = 0

        for (para in paragraphs) {
            val paraText = para.text
            val paraLength = paraText.length

            elementsXml.append(
                "<paragraph SpaceAbove=\"1.0\" SpaceBelow=\"1.0\" LeftIndent=\"0.0\" " +
                    "RightIndent=\"0.0\" LineSpacing=\"0.0\" resolver=\"hvl-default\" " +
                    "Alignment=\"${para.alignment}\" Hanging=\"0.0\">",
            )

            if (paraLength == 0) {
                elementsXml.append("<content resolver=\"hvl-default\" startOffset=\"$currentOffset\" length=\"0\" />")
                currentOffset += 1
            } else {
                var runOffset = currentOffset
                for (run in para.runs) {
                    val runLength = run.text.length
                    if (runLength == 0) continue

                    val attrs = StringBuilder(" resolver=\"hvl-default\"")
                    if (run.isBold) attrs.append(" bold=\"true\"")
                    if (run.isItalic) attrs.append(" italic=\"true\"")
                    if (run.isUnderline) attrs.append(" underline=\"true\"")
                    if (run.fontSize != 12f && run.fontSize > 0f) {
                        attrs.append(" size=\"${run.fontSize.toInt()}\"")
                    }
                    val family = run.fontFamily.ifEmpty { "Times New Roman" }
                    attrs.append(" family=\"${escapeXml(family)}\"")
                    attrs.append(" startOffset=\"$runOffset\" length=\"$runLength\"")

                    elementsXml.append("<content$attrs />")
                    runOffset += runLength
                }
                currentOffset += paraLength + 1
            }

            elementsXml.append("</paragraph>\n")
        }

        return buildContentXmlFromElements(cdataText = cdataText, elementsXml = elementsXml.toString())
    }

    /** `<template>` / `<properties>` / `<elements>` (+ isteğe bağlı headers/footers) / `<styles>` sarmalayıcısı — paragraf ve zengin düzenleme akışları arasında paylaşılır. */
    private fun buildContentXmlFromElements(
        cdataText: String,
        elementsXml: String,
        headersXml: String? = null,
        footersXml: String? = null,
    ): String {
        val stylesXml = "<styles>" +
            "<style name=\"default\" italic=\"false\" description=\"Geçerli\" size=\"12\" " +
            "RightIndent=\"15.0\" bold=\"false\" family=\"Dialog\" foreground=\"-16777216\" " +
            "FONT_ATTRIBUTE_KEY=\"javax.swing.plaf.FontUIResource[family=Dialog,name=Dialog,style=plain,size=12]\" />" +
            "<style name=\"hvl-default\" SpaceAbove=\"0.0\" description=\"Gövde\" SpaceBelow=\"0.0\" " +
            "size=\"12\" LeftIndent=\"0.0\" RightIndent=\"0.0\" LineSpacing=\"0.0\" Alignment=\"0\" " +
            "family=\"Times New Roman\" />" +
            "</styles>"

        val sections = StringBuilder("<elements >$elementsXml</elements>\n")
        if (headersXml != null) sections.append("<headers>$headersXml</headers>\n")
        if (footersXml != null) sections.append("<footers>$footersXml</footers>\n")

        return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?> \n\n" +
            "<template format_id=\"1.8\" >\n" +
            "<content><![CDATA[$cdataText]]></content>" +
            "<properties>" +
            "<pageFormat mediaSizeName=\"1\" leftMargin=\"56.69291305541992\" rightMargin=\"56.69291305541992\" " +
            "topMargin=\"28.34645652770996\" bottomMargin=\"28.34645652770996\" paperOrientation=\"1\" " +
            "headerFOffset=\"15.0\" footerFOffset=\"60.00944846916199\" />" +
            "</properties>\n" +
            sections.toString() +
            stylesXml + "\n" +
            "</template>\n"
    }

    private fun buildPropertiesXml(): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">\n" +
            "<properties>\n" +
            "<entry key=\"uyapdogrulamakodu\"></entry>\n" +
            "</properties>"

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
