package com.velikececi.udfdonusturucu.core.tools.editor

/**
 * UDFStructureParser.swift'in Kotlin karşılığı. UYAP content.xml içindeki `<elements>` bölümünü
 * yapılandırılmış düzenleme modeline çevirir.
 *
 * Swift'in `Character` tabanlı String aralıklarının yerine Kotlin'in UTF-16 kod birimi tabanlı
 * `indexOf`/`substring`'i kullanılır — Türkçe alfabesi (ç ğ ı İ ö ş ü dahil) tamamen Temel Çok
 * Dilli Düzlem (BMP) içinde tek kod birimli olduğundan bu iki yaklaşım pratikte eşdeğerdir.
 * [UdfStructureWriter] de aynı UTF-16 tabanlı ofsetlerle yazdığından, Android'in kendi
 * yaz→oku döngüsü her koşulda kendi içinde tutarlıdır.
 */
object UdfStructureParser {

    fun parse(rawXml: String, plainText: String): UdfEditDocument {
        val headers = parseHeaderFooterContainer(rawXml, container = "headers", item = "header", plainText = plainText)
        val footers = parseHeaderFooterContainer(rawXml, container = "footers", item = "footer", plainText = plainText)

        if (!rawXml.contains("<elements")) {
            return fallbackDocument(plainText).copy(headers = headers, footers = footers)
        }

        val elementsBody = extractElementsBody(rawXml)
            ?: return fallbackDocument(plainText).copy(headers = headers, footers = footers)

        val blocks = mutableListOf<UdfEditBlock>()
        var search = 0

        // iOS kaynağından KASITLI SAPMA: Swift sürümü her turda önce "<paragraph "i (aramanın
        // KALAN tamamında, en yakın olan değil) dener; bu, bir <table> ilk paragraftan önce
        // geldiğinde o tabloyu sessizce atlayıp veri kaybına yol açıyordu. Burada iki etiketten
        // METİNDE DAHA ÖNCE geleni işlenir — UYAP'tan gelen gerçek dosyalarda (ör. üstte tablo
        // içeren dilekçeler) veri kaybını önler.
        while (search < elementsBody.length) {
            val paraStart = elementsBody.indexOf("<paragraph ", search)
            val tableStart = elementsBody.indexOf("<table", search)

            val nextIsParagraph = when {
                paraStart == -1 -> false
                tableStart == -1 -> true
                else -> paraStart <= tableStart
            }
            if (paraStart == -1 && tableStart == -1) break

            if (nextIsParagraph) {
                val end = elementsBody.indexOf("</paragraph>", paraStart)
                if (end == -1) break
                val xml = elementsBody.substring(paraStart, end + "</paragraph>".length)
                blocks.add(UdfEditBlock.ParagraphBlock(parseParagraph(xml, plainText)))
                search = end + "</paragraph>".length
            } else {
                val end = elementsBody.indexOf("</table>", tableStart)
                if (end == -1) break
                val xml = elementsBody.substring(tableStart, end + "</table>".length)
                blocks.add(UdfEditBlock.TableBlock(parseTable(xml, plainText)))
                search = end + "</table>".length
            }
        }

        if (blocks.isEmpty()) {
            return fallbackDocument(plainText).copy(headers = headers, footers = footers)
        }
        return UdfEditDocument(headers = headers, blocks = blocks, footers = footers)
    }

    // MARK: - Header / Footer

    private fun parseHeaderFooterContainer(xml: String, container: String, item: String, plainText: String): List<UdfEditHeaderFooter> {
        val body = extractTaggedBody(xml, openTag = "<$container", closeTag = "</$container>") ?: return emptyList()

        val items = mutableListOf<UdfEditHeaderFooter>()
        var search = 0
        val openItem = "<$item"

        while (true) {
            val start = body.indexOf(openItem, search)
            if (start == -1) break
            val end = body.indexOf("</$item>", start)
            if (end == -1) break
            val itemXml = body.substring(start, end + "</$item>".length)
            val type = extractStringAttr(itemXml, "type") ?: "default"

            val paragraphs = mutableListOf<UdfEditParagraph>()
            var pSearch = 0
            while (true) {
                val pStart = itemXml.indexOf("<paragraph ", pSearch)
                if (pStart == -1) break
                val pEnd = itemXml.indexOf("</paragraph>", pStart)
                if (pEnd == -1) break
                paragraphs.add(parseParagraph(itemXml.substring(pStart, pEnd + "</paragraph>".length), plainText))
                pSearch = pEnd + "</paragraph>".length
            }

            if (paragraphs.isEmpty()) {
                val pStart = itemXml.indexOf("<paragraph")
                if (pStart != -1) {
                    val pEnd = itemXml.indexOf("</paragraph>", pStart)
                    if (pEnd != -1) {
                        paragraphs.add(parseParagraph(itemXml.substring(pStart, pEnd + "</paragraph>".length), plainText))
                    }
                }
            }

            if (paragraphs.isEmpty()) {
                paragraphs.add(UdfEditParagraph(runs = listOf(UdfEditRun(text = ""))))
            }

            items.add(UdfEditHeaderFooter(type = type, paragraphs = paragraphs))
            search = end + "</$item>".length
        }

        return items
    }

    // MARK: - Paragraph

    private fun parseParagraph(xml: String, plainText: String): UdfEditParagraph =
        UdfEditParagraph(
            alignment = extractIntAttr(xml, "Alignment") ?: 3,
            spaceAbove = extractFloatAttr(xml, "SpaceAbove") ?: 1f,
            spaceBelow = extractFloatAttr(xml, "SpaceBelow") ?: 1f,
            leftIndent = extractFloatAttr(xml, "LeftIndent") ?: 0f,
            rightIndent = extractFloatAttr(xml, "RightIndent") ?: 0f,
            firstLineIndent = extractFloatAttr(xml, "FirstLineIndent") ?: 0f,
            hangingIndent = extractFloatAttr(xml, "Hanging") ?: 0f,
            lineSpacing = extractFloatAttr(xml, "LineSpacing") ?: 0f,
            tabStops = parseTabStops(xml),
            runs = parseRuns(xml, plainText),
        )

    private data class TagRun(
        val kind: UdfRunKind,
        val startOffset: Int,
        val length: Int,
        val bold: Boolean,
        val italic: Boolean,
        val underline: Boolean,
        val fontSize: Float?,
        val fontFamily: String?,
        val foreground: Int?,
        val background: Int?,
    )

    private fun parseRuns(xml: String, plainText: String): List<UdfEditRun> {
        val tagged = mutableListOf<TagRun>()
        val patterns = listOf("<content " to "content", "<field " to "field", "<space " to "space")

        for ((pattern, kindTag) in patterns) {
            var search = 0
            while (true) {
                val tagStart = xml.indexOf(pattern, search)
                if (tagStart == -1) break
                val tagEnd = xml.indexOf("/>", tagStart)
                if (tagEnd == -1) break
                val tag = xml.substring(tagStart, tagEnd + 2)

                val start = extractIntAttr(tag, "startOffset")
                val length = extractIntAttr(tag, "length")
                if (start == null || length == null) {
                    search = tagEnd + 2
                    continue
                }

                val kind = when (kindTag) {
                    "field" -> {
                        val name = extractStringAttr(tag, "fieldName")
                            ?: extractStringAttr(tag, "name")
                            ?: extractStringAttr(tag, "FieldName")
                            ?: "alan"
                        UdfRunKind.Field(name)
                    }
                    "space" -> UdfRunKind.Space
                    else -> UdfRunKind.Content
                }

                tagged.add(
                    TagRun(
                        kind = kind,
                        startOffset = start,
                        length = length,
                        bold = tag.contains("bold=\"true\"", ignoreCase = true),
                        italic = tag.contains("italic=\"true\"", ignoreCase = true),
                        underline = tag.contains("underline=\"true\"", ignoreCase = true),
                        fontSize = extractFloatAttr(tag, "size") ?: extractFloatAttr(tag, "Size"),
                        fontFamily = extractStringAttr(tag, "family") ?: extractStringAttr(tag, "Family"),
                        foreground = UdfColorCodec.parse(extractStringAttr(tag, "foreground"))
                            ?: UdfColorCodec.parse(extractStringAttr(tag, "Foreground")),
                        background = UdfColorCodec.parse(extractStringAttr(tag, "background"))
                            ?: UdfColorCodec.parse(extractStringAttr(tag, "Background")),
                    ),
                )
                search = tagEnd + 2
            }
        }

        tagged.sortBy { it.startOffset }

        return tagged.map { tag ->
            UdfEditRun(
                kind = tag.kind,
                text = sliceText(plainText, tag.startOffset, tag.length),
                isBold = tag.bold,
                isItalic = tag.italic,
                isUnderline = tag.underline,
                fontSize = tag.fontSize ?: 12f,
                fontFamily = tag.fontFamily ?: "Times New Roman",
                foregroundARGB = tag.foreground,
                backgroundARGB = tag.background,
            )
        }
    }

    // MARK: - Table

    private fun parseTable(xml: String, plainText: String): UdfEditTable {
        val columnCount = extractIntAttr(xml, "columnCount") ?: 2
        val spansString = extractStringAttr(xml, "columnSpans") ?: ""
        val columnSpans = spansString.split(",").mapNotNull { it.trim().toIntOrNull() }
        val border = extractStringAttr(xml, "border") ?: "borderCell"

        val rows = mutableListOf<UdfEditTableRow>()
        var search = 0
        while (true) {
            val rowStart = xml.indexOf("<row", search)
            if (rowStart == -1) break
            val rowEnd = xml.indexOf("</row>", rowStart)
            if (rowEnd == -1) break
            rows.add(parseTableRow(xml.substring(rowStart, rowEnd + "</row>".length), plainText))
            search = rowEnd + "</row>".length
        }

        return UdfEditTable(
            columnCount = maxOf(columnCount, 1),
            columnSpans = columnSpans,
            border = border,
            rows = rows,
        )
    }

    private fun parseTableRow(xml: String, plainText: String): UdfEditTableRow {
        val rowType = extractStringAttr(xml, "rowType") ?: "dataRow"
        val cells = mutableListOf<UdfEditTableCell>()
        var search = 0
        while (true) {
            val cellStart = xml.indexOf("<cell", search)
            if (cellStart == -1) break
            val cellEnd = xml.indexOf("</cell>", cellStart)
            if (cellEnd == -1) break
            cells.add(parseTableCell(xml.substring(cellStart, cellEnd + "</cell>".length), plainText))
            search = cellEnd + "</cell>".length
        }
        return UdfEditTableRow(rowType = rowType, cells = cells)
    }

    private fun parseTableCell(xml: String, plainText: String): UdfEditTableCell {
        val colspan = extractIntAttr(xml, "colspan") ?: 1
        val rowspan = extractIntAttr(xml, "rowspan") ?: 1
        val fill = UdfColorCodec.parse(extractStringAttr(xml, "fillColor"))
            ?: UdfColorCodec.parse(extractStringAttr(xml, "background"))

        val paragraphs = mutableListOf<UdfEditParagraph>()
        var search = 0
        while (true) {
            val pStart = xml.indexOf("<paragraph ", search)
            if (pStart == -1) break
            val pEnd = xml.indexOf("</paragraph>", pStart)
            if (pEnd == -1) break
            paragraphs.add(parseParagraph(xml.substring(pStart, pEnd + "</paragraph>".length), plainText))
            search = pEnd + "</paragraph>".length
        }

        if (paragraphs.isEmpty()) {
            val pStart = xml.indexOf("<paragraph")
            if (pStart != -1) {
                val pEnd = xml.indexOf("</paragraph>", pStart)
                if (pEnd != -1) {
                    paragraphs.add(parseParagraph(xml.substring(pStart, pEnd + "</paragraph>".length), plainText))
                }
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(UdfEditParagraph(runs = listOf(UdfEditRun(text = ""))))
        }

        return UdfEditTableCell(
            colspan = maxOf(colspan, 1),
            rowspan = maxOf(rowspan, 1),
            fillColorARGB = fill,
            paragraphs = paragraphs,
        )
    }

    // MARK: - Helpers

    private fun fallbackDocument(text: String): UdfEditDocument {
        val paragraphs = text.split("\n").map { line -> UdfEditParagraph(alignment = 3, runs = listOf(UdfEditRun(text = line))) }
        return UdfEditDocument(blocks = paragraphs.map { UdfEditBlock.ParagraphBlock(it) })
    }

    private fun extractTaggedBody(xml: String, openTag: String, closeTag: String): String? {
        val start = xml.indexOf(openTag)
        if (start == -1) return null
        val openEnd = xml.indexOf(">", start)
        if (openEnd == -1) return null
        val close = xml.indexOf(closeTag, openEnd + 1)
        if (close == -1) return null
        return xml.substring(openEnd + 1, close)
    }

    private fun extractElementsBody(xml: String): String? {
        val start = xml.indexOf("<elements")
        if (start == -1) return null
        val openEnd = xml.indexOf(">", start)
        if (openEnd == -1) return null
        val close = xml.indexOf("</elements>", openEnd + 1)
        if (close == -1) return null
        return xml.substring(openEnd + 1, close)
    }

    private fun sliceText(text: String, start: Int, length: Int): String {
        if (start < 0 || length < 0 || start >= text.length) return ""
        val end = minOf(start + length, text.length)
        if (start >= end) return ""
        return text.substring(start, end)
    }

    private fun parseTabStops(xml: String): List<Float> {
        val tabStr = extractStringAttr(xml, "TabSet") ?: return emptyList()
        val parts = tabStr.split(":")
        val pos = parts.firstOrNull()?.toDoubleOrNull()?.toFloat()
        return if (pos != null && pos > 0) listOf(pos) else emptyList()
    }

    /** Swift'in `String.capitalized`'ının tek-kelime (boşluksuz) davranışı: ilk harf büyük, gerisi küçük. */
    private fun swiftCapitalized(value: String): String =
        if (value.isEmpty()) value else value[0].uppercaseChar() + value.substring(1).lowercase()

    private fun attrKeyVariants(name: String): List<String> = listOf(name, name.lowercase(), swiftCapitalized(name))

    private fun extractIntAttr(xml: String, name: String): Int? {
        for (key in attrKeyVariants(name)) {
            val idx = xml.indexOf("$key=\"")
            if (idx == -1) continue
            val start = idx + key.length + 2
            val end = xml.indexOf("\"", start)
            if (end == -1) continue
            xml.substring(start, end).toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun extractFloatAttr(xml: String, name: String): Float? {
        for (key in attrKeyVariants(name)) {
            val idx = xml.indexOf("$key=\"")
            if (idx == -1) continue
            val start = idx + key.length + 2
            val end = xml.indexOf("\"", start)
            if (end == -1) continue
            xml.substring(start, end).toDoubleOrNull()?.let { return it.toFloat() }
        }
        return null
    }

    private fun extractStringAttr(xml: String, name: String): String? {
        for (key in attrKeyVariants(name)) {
            val idx = xml.indexOf("$key=\"")
            if (idx == -1) continue
            val start = idx + key.length + 2
            val end = xml.indexOf("\"", start)
            if (end == -1) continue
            val value = xml.substring(start, end)
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
