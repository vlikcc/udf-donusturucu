package com.velikececi.udfdonusturucu.core.tools.editor

/** UDFStructureWriter.swift'in Kotlin karşılığı — düzenleme modelini UYAP content.xml (CDATA + elements + headers + footers) biçimine yazar. */
object UdfStructureWriter {

    data class BuildResult(
        val cdataText: String,
        val elementsXML: String,
        val headersXML: String?,
        val footersXML: String?,
    )

    fun build(document: UdfEditDocument): BuildResult {
        val cdata = StringBuilder()
        val offset = IntRef(0)

        val headersXml = writeHeaderFooterSection(document.headers, itemTag = "header", cdata = cdata, offset = offset)

        val elements = StringBuilder("\n")
        for (block in document.blocks) {
            when (block) {
                is UdfEditBlock.ParagraphBlock -> elements.append(writeParagraph(block.paragraph, cdata, offset))
                is UdfEditBlock.TableBlock -> elements.append(writeTable(block.table, cdata, offset))
            }
        }

        val footersXml = writeHeaderFooterSection(document.footers, itemTag = "footer", cdata = cdata, offset = offset)

        return BuildResult(
            cdataText = cdata.toString(),
            elementsXML = elements.toString(),
            headersXML = headersXml,
            footersXML = footersXml,
        )
    }

    /** Swift'in `inout Int` ofset parametresinin Kotlin karşılığı — tek elemanlı taşıyıcı kutu. */
    private class IntRef(var value: Int)

    // MARK: - Header / Footer

    private fun writeHeaderFooterSection(items: List<UdfEditHeaderFooter>, itemTag: String, cdata: StringBuilder, offset: IntRef): String? {
        if (items.isEmpty()) return null
        val xml = StringBuilder("\n")
        for (item in items) {
            xml.append("  <$itemTag type=\"${escape(item.type)}\">\n")
            for (para in item.paragraphs) {
                xml.append(writeParagraph(para, cdata, offset, indent = "    "))
            }
            xml.append("  </$itemTag>\n")
        }
        return xml.toString()
    }

    // MARK: - Paragraph

    private fun writeParagraph(para: UdfEditParagraph, cdata: StringBuilder, offset: IntRef, indent: String = ""): String {
        var tabAttr = ""
        if (para.tabStops.isNotEmpty()) {
            tabAttr = " TabSet=\"${formatFloat(para.tabStops.first())}:0:0\""
        }

        val xml = StringBuilder(
            "$indent<paragraph SpaceAbove=\"${formatFloat(para.spaceAbove)}\" SpaceBelow=\"${formatFloat(para.spaceBelow)}\" " +
                "LeftIndent=\"${formatFloat(para.leftIndent)}\" RightIndent=\"${formatFloat(para.rightIndent)}\" " +
                "LineSpacing=\"${formatFloat(para.lineSpacing)}\" resolver=\"hvl-default\" " +
                "Alignment=\"${para.alignment}\" Hanging=\"${formatFloat(para.hangingIndent)}\"$tabAttr>",
        )

        val paraText = para.runs.joinToString("") { it.text }
        var runOffset = offset.value

        if (paraText.isEmpty()) {
            xml.append("<content resolver=\"hvl-default\" startOffset=\"${offset.value}\" length=\"0\" />")
            cdata.append("\n")
            offset.value += 1
        } else {
            for (run in para.runs) {
                if (run.text.isEmpty()) continue
                xml.append(runTag(run, startOffset = runOffset, length = run.text.length))
                runOffset += run.text.length
            }
            cdata.append(paraText).append("\n")
            offset.value += paraText.length + 1
        }

        xml.append("</paragraph>\n")
        return xml.toString()
    }

    // MARK: - Table

    private fun writeTable(table: UdfEditTable, cdata: StringBuilder, offset: IntRef): String {
        val spans = table.columnSpans.joinToString(",")
        val spansAttr = if (spans.isEmpty()) "" else " columnSpans=\"$spans\""
        val xml = StringBuilder("<table tableName=\"Tablo\" columnCount=\"${table.columnCount}\"$spansAttr border=\"${escape(table.border)}\">\n")

        for (row in table.rows) {
            xml.append("  <row rowName=\"row\" rowType=\"${escape(row.rowType)}\" border=\"${escape(table.border)}\">\n")
            for (cell in row.cells) {
                val cellAttrs = StringBuilder(
                    "colspan=\"${cell.colspan}\" rowspan=\"${cell.rowspan}\" align=\"top\" border=\"${escape(table.border)}\" borderSpec=\"15\"",
                )
                if (cell.fillColorARGB != null) {
                    cellAttrs.append(" fillColor=\"${cell.fillColorARGB}\"")
                }
                xml.append("    <cell $cellAttrs>\n")
                for (para in cell.paragraphs) {
                    xml.append(writeParagraph(para, cdata, offset, indent = "      "))
                }
                xml.append("    </cell>\n")
            }
            xml.append("  </row>\n")
        }

        xml.append("</table>\n")
        return xml.toString()
    }

    // MARK: - Run tag

    private fun runTag(run: UdfEditRun, startOffset: Int, length: Int): String {
        val tagName = when (run.kind) {
            is UdfRunKind.Content -> "content"
            is UdfRunKind.Field -> "field"
            is UdfRunKind.Space -> "space"
        }

        val attrs = StringBuilder(" resolver=\"hvl-default\"")
        if (run.isBold) attrs.append(" bold=\"true\"")
        if (run.isItalic) attrs.append(" italic=\"true\"")
        if (run.isUnderline) attrs.append(" underline=\"true\"")
        // Yalnızca varsayılan (12) dışında bir boyut varsa yazılır — iOS ile aynı, dosyayı gereksiz şişirmez.
        if (run.fontSize != 12f && run.fontSize > 0f) {
            attrs.append(" size=\"${run.fontSize.toInt()}\"")
        }
        attrs.append(" family=\"${escape(run.fontFamily)}\"")
        run.foregroundARGB?.let { attrs.append(" foreground=\"$it\"") }
        run.backgroundARGB?.let { attrs.append(" background=\"$it\"") }
        if (run.kind is UdfRunKind.Field) {
            attrs.append(" fieldName=\"${escape(run.kind.name)}\"")
        }
        attrs.append(" startOffset=\"$startOffset\" length=\"$length\"")
        return "<$tagName$attrs />"
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Swift'in `"\(CGFloat)"` enterpolasyonu tam sayı değerleri bile ondalıklı ("1.0") yazar; UYAP'ın kendi ayrıştırıcısıyla tutarlılık için aynısı yapılır. */
    private fun formatFloat(value: Float): String {
        val d = value.toDouble()
        return if (d == d.toLong().toDouble()) "${d.toLong()}.0" else d.toString()
    }
}
