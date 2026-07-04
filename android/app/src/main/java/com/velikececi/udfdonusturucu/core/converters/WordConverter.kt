package com.velikececi.udfdonusturucu.core.converters

import android.content.Context
import com.velikececi.udfdonusturucu.core.model.ConversionException
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.model.UdfTable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * WordConverter.swift'in Kotlin karşılığı. iOS tarafı OOXML parçalarını geçici bir dizine
 * yazıp `NSFileCoordinator` ile dizini zip'e çeviriyordu (yalnızca Apple platformlarına özgü
 * bir teknik); Android'de aynı XML şablonları doğrudan `ZipOutputStream`e yazılır — ayrı
 * dizin/temizlik adımına gerek yok.
 */
object WordConverter {

    fun convert(document: UdfDocument, context: Context): File {
        val outputFile = OutputPaths.outputFile(context, document.fileName, "docx")

        try {
            ZipOutputStream(outputFile.outputStream()).use { zip ->
                writeEntry(zip, "[Content_Types].xml", contentTypesXml())
                writeEntry(zip, "_rels/.rels", relsXml())
                writeEntry(zip, "word/_rels/document.xml.rels", wordRelsXml())
                writeEntry(zip, "word/styles.xml", stylesXml())
                writeEntry(zip, "word/document.xml", buildDocumentXml(document))
            }
        } catch (e: Exception) {
            throw ConversionException.DocxCreationFailed()
        }

        return outputFile
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        val entry = ZipEntry(name).apply { method = ZipEntry.DEFLATED }
        zip.putNextEntry(entry)
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // MARK: - OOXML Templates

    private fun contentTypesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
        </Types>
    """.trimIndent()

    private fun relsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun wordRelsXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    private fun stylesXml() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
            <w:style w:type="paragraph" w:styleId="Normal" w:default="1">
                <w:name w:val="Normal"/>
                <w:rPr>
                    <w:sz w:val="22"/>
                    <w:szCs w:val="22"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading1">
                <w:name w:val="heading 1"/>
                <w:pPr><w:outlineLvl w:val="0"/></w:pPr>
                <w:rPr>
                    <w:b/>
                    <w:sz w:val="32"/>
                    <w:szCs w:val="32"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
            <w:style w:type="paragraph" w:styleId="Heading2">
                <w:name w:val="heading 2"/>
                <w:pPr><w:outlineLvl w:val="1"/></w:pPr>
                <w:rPr>
                    <w:b/>
                    <w:sz w:val="26"/>
                    <w:szCs w:val="26"/>
                    <w:rFonts w:ascii="Calibri" w:hAnsi="Calibri"/>
                </w:rPr>
            </w:style>
        </w:styles>
    """.trimIndent()

    // MARK: - Document Building

    private fun buildDocumentXml(document: UdfDocument): String {
        val body = StringBuilder()

        document.metadata?.let { meta ->
            meta.title?.let { body.append(paragraph(text = it, style = "Heading1")) }
            meta.author?.let { body.append(paragraph(text = "Yazar: $it", italic = true, fontSize = 18)) }
            meta.creationDate?.let { body.append(paragraph(text = "Tarih: $it", italic = true, fontSize = 18)) }
            body.append(paragraph(text = ""))
        }

        if (document.content.contentType == UdfContentType.UYAP || document.content.sections.isEmpty()) {
            document.content.text.split("\n").forEach { line -> body.append(paragraph(text = line)) }
        } else {
            for (section in document.content.sections) {
                section.title?.let { body.append(paragraph(text = it, style = "Heading2")) }
                section.body.split("\n").forEach { line -> body.append(paragraph(text = line)) }
            }
        }

        for (table in document.content.tables) {
            body.append(buildTableXml(table))
        }

        return """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <w:body>
                $body
            </w:body>
        </w:document>
        """.trimIndent()
    }

    private fun paragraph(
        text: String,
        style: String? = null,
        bold: Boolean = false,
        italic: Boolean = false,
        fontSize: Int? = null,
    ): String {
        val escapedText = escapeXml(text)
        val pPr = if (style != null) "<w:pStyle w:val=\"$style\"/>" else ""

        val rPr = StringBuilder()
        if (bold) rPr.append("<w:b/>")
        if (italic) rPr.append("<w:i/>")
        if (fontSize != null) rPr.append("<w:sz w:val=\"$fontSize\"/><w:szCs w:val=\"$fontSize\"/>")

        val pPrBlock = if (pPr.isEmpty()) "" else "<w:pPr>$pPr</w:pPr>"
        val rPrBlock = if (rPr.isEmpty()) "" else "<w:rPr>$rPr</w:rPr>"

        return "<w:p>$pPrBlock<w:r>$rPrBlock<w:t xml:space=\"preserve\">$escapedText</w:t></w:r></w:p>"
    }

    private fun buildTableXml(table: UdfTable): String {
        val xml = StringBuilder(
            """
            <w:tbl>
                <w:tblPr>
                    <w:tblBorders>
                        <w:top w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:left w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:bottom w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:right w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:insideH w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                        <w:insideV w:val="single" w:sz="4" w:space="0" w:color="auto"/>
                    </w:tblBorders>
                </w:tblPr>
            """.trimIndent(),
        )

        for (row in table.rows) {
            xml.append("<w:tr>")
            for (cell in row) {
                xml.append("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">${escapeXml(cell)}</w:t></w:r></w:p></w:tc>")
            }
            xml.append("</w:tr>")
        }

        xml.append("</w:tbl>")
        return xml.toString()
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
