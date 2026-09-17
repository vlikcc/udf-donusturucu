package com.velikececi.udfdonusturucu.core.parser

import com.velikececi.udfdonusturucu.core.model.UdfContent
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.model.UdfMetadata
import com.velikececi.udfdonusturucu.core.model.UdfPageFormat
import com.velikececi.udfdonusturucu.core.model.UdfParserException
import com.velikececi.udfdonusturucu.core.model.UdfSection
import com.velikececi.udfdonusturucu.core.model.UdfTable
import com.velikececi.udfdonusturucu.core.model.UyapParagraph
import com.velikececi.udfdonusturucu.core.model.UyapTextRun
import com.velikececi.udfdonusturucu.core.zip.UdfZip
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * UDFParser.swift'in (594 satır) Kotlin karşılığı. iOS'taki `NSAttributedString` üretimi
 * (`buildAttributedString`) burada yapılmaz; bunun yerine ayrıştırılmış [UyapParagraph] listesi
 * doğrudan [UdfContent.paragraphs] alanında döner — biçimlendirilmiş metnin Android tarafındaki
 * (Spannable/AnnotatedString) karşılığı Faz 3'teki dönüştürücülerde inşa edilir.
 */
object UdfParser {

    private val WINDOWS_1254: Charset? = runCatching { Charset.forName("windows-1254") }.getOrNull()

    fun parse(file: File): UdfDocument {
        if (!file.exists()) {
            throw UdfParserException.FileNotFound()
        }

        val entries = UdfZip.extractEntries(file)

        var contentEntry: UdfZip.Entry? = null
        var metadataEntry: UdfZip.Entry? = null

        for (entry in entries) {
            val name = entry.fileName.lowercase(Locale.ROOT)
            if (name.contains("content") || name.endsWith(".xml") || name.endsWith(".rtf") ||
                name.endsWith(".html") || name.endsWith(".htm")
            ) {
                if (contentEntry == null) contentEntry = entry
            }
            if (name.contains("meta") || name.contains("info") || name.contains("propert")) {
                metadataEntry = entry
            }
        }

        if (contentEntry == null) {
            contentEntry = entries
                .filterNot { e ->
                    val n = e.fileName.lowercase(Locale.ROOT)
                    n.contains("imza") || n.contains("sign") || n.contains(".p7s") || n.contains(".sig")
                }
                .maxByOrNull { it.data.size }
        }

        val content = contentEntry ?: throw UdfParserException.NoContentFound()

        val decodedText = decodeBestEffort(content.data)
            ?: throw UdfParserException.ParsingFailed("İçerik kodlaması tanınmadı.")

        val udfContent = parseContent(decodedText)
        val udfMetadata = metadataEntry?.let { runCatching { parseMetadata(it) }.getOrNull() }
        val pageFormat = parsePageFormat(decodedText)

        return UdfDocument(
            fileName = file.name,
            content = udfContent,
            metadata = udfMetadata,
            pageFormat = pageFormat,
        )
    }

    // MARK: - Content Detection

    private fun parseContent(text: String): UdfContent {
        if (text.contains("<template") && text.contains("<![CDATA[") && text.contains("<elements")) {
            return parseUyapContent(text)
        }

        val isRtf = text.startsWith("{\\rtf")
        val lower = text.lowercase(Locale.ROOT)
        val isHtml = lower.contains("<html") || lower.contains("<body")

        return when {
            isRtf -> {
                val (plainText, paragraphs) = parseRtfFormatted(text)
                UdfContent(
                    text = plainText,
                    rawContent = text,
                    contentType = UdfContentType.RTF,
                    sections = emptyList(),
                    tables = emptyList(),
                    isRtf = true,
                    paragraphs = paragraphs,
                )
            }
            isHtml -> {
                val plain = stripHtmlTags(text)
                UdfContent(
                    text = plain,
                    rawContent = text,
                    contentType = UdfContentType.HTML,
                    sections = extractSections(plain),
                    tables = extractHtmlTables(text),
                    isRtf = false,
                    paragraphs = emptyList(),
                )
            }
            else -> UdfContent(
                text = text,
                rawContent = text,
                contentType = UdfContentType.PLAIN_TEXT,
                sections = extractSections(text),
                tables = emptyList(),
                isRtf = false,
                paragraphs = emptyList(),
            )
        }
    }

    // MARK: - UYAP Format Parsing

    private fun parseUyapContent(xml: String): UdfContent {
        val plainText = extractCdata(xml) ?: ""
        val paragraphs = parseUyapElements(xml)
        return UdfContent(
            text = plainText,
            rawContent = xml,
            contentType = UdfContentType.UYAP,
            sections = emptyList(),
            tables = emptyList(),
            isRtf = false,
            paragraphs = paragraphs,
        )
    }

    private fun parseUyapElements(xml: String): List<UyapParagraph> {
        val elementsStart = xml.indexOf("<elements")
        if (elementsStart < 0) return emptyList()
        val elementsXml = xml.substring(elementsStart)

        val paragraphs = mutableListOf<UyapParagraph>()
        var searchStart = 0
        while (true) {
            val pStart = elementsXml.indexOf("<paragraph ", searchStart)
            if (pStart < 0) break
            val pEndTag = elementsXml.indexOf("</paragraph>", pStart)
            if (pEndTag < 0) break
            val pEnd = pEndTag + "</paragraph>".length

            val paragraphXml = elementsXml.substring(pStart, pEnd)

            val alignment = extractIntAttr(paragraphXml, "Alignment") ?: 0
            val spaceAbove = extractFloatAttr(paragraphXml, "SpaceAbove") ?: 1.0f
            val spaceBelow = extractFloatAttr(paragraphXml, "SpaceBelow") ?: 1.0f
            val leftIndent = extractFloatAttr(paragraphXml, "LeftIndent") ?: 0f
            val rightIndent = extractFloatAttr(paragraphXml, "RightIndent") ?: 0f
            val firstLineIndent = extractFloatAttr(paragraphXml, "FirstLineIndent") ?: 0f
            val hanging = extractFloatAttr(paragraphXml, "Hanging") ?: 0f
            val lineSpacing = extractFloatAttr(paragraphXml, "LineSpacing") ?: 0f

            val tabStops = parseTabStops(paragraphXml)
            val runs = parseTextRuns(paragraphXml)

            paragraphs.add(
                UyapParagraph(
                    alignment = alignment,
                    spaceAbove = spaceAbove,
                    spaceBelow = spaceBelow,
                    leftIndent = leftIndent,
                    rightIndent = rightIndent,
                    firstLineIndent = firstLineIndent,
                    hangingIndent = hanging,
                    lineSpacing = lineSpacing,
                    tabStops = tabStops,
                    runs = runs,
                ),
            )

            searchStart = pEnd
        }

        return paragraphs
    }

    private fun parseTextRuns(paragraphXml: String): List<UyapTextRun> {
        val runs = mutableListOf<UyapTextRun>()
        val tagPatterns = listOf("<content ", "<field ", "<space ")

        for (tagPattern in tagPatterns) {
            var search = 0
            while (true) {
                val tagStart = paragraphXml.indexOf(tagPattern, search)
                if (tagStart < 0) break

                val tagEndMarker = paragraphXml.indexOf("/>", tagStart)
                if (tagEndMarker < 0) {
                    search = tagStart + tagPattern.length
                    continue
                }
                val tagEnd = tagEndMarker + "/>".length

                val tagContent = paragraphXml.substring(tagStart, tagEnd)

                val startOffset = extractIntAttr(tagContent, "startOffset")
                val length = extractIntAttr(tagContent, "length")
                if (startOffset == null || length == null) {
                    search = tagEnd
                    continue
                }

                val bold = tagContent.contains("bold=\"true\"")
                val underline = tagContent.contains("underline=\"true\"")
                val italic = tagContent.contains("italic=\"true\"")
                val fontSize = extractFloatAttr(tagContent, "size")
                val fontFamily = extractStringAttr(tagContent, "family")

                runs.add(
                    UyapTextRun(
                        startOffset = startOffset,
                        length = length,
                        bold = bold,
                        underline = underline,
                        italic = italic,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                    ),
                )

                search = tagEnd
            }
        }

        return runs.sortedBy { it.startOffset }
    }

    private fun parseTabStops(xml: String): List<Float> {
        val tabStr = extractStringAttr(xml, "TabSet") ?: return emptyList()
        // Biçim: "130.0:0:0" — ilk sayı konumdur
        val pos = tabStr.substringBefore(':').toDoubleOrNull()?.toFloat() ?: return emptyList()
        return if (pos > 0) listOf(pos) else emptyList()
    }

    // MARK: - Page Format Parsing

    private fun parsePageFormat(text: String): UdfPageFormat? {
        if (!text.contains("<pageFormat")) return null

        val left = extractFloatAttr(text, "leftMargin") ?: 56.69f
        val right = extractFloatAttr(text, "rightMargin") ?: 56.69f
        val top = extractFloatAttr(text, "topMargin") ?: 28.35f
        val bottom = extractFloatAttr(text, "bottomMargin") ?: 28.35f

        return UdfPageFormat(leftMargin = left, rightMargin = right, topMargin = top, bottomMargin = bottom)
    }

    // MARK: - XML Attribute Helpers

    private fun extractIntAttr(xml: String, name: String): Int? {
        val value = extractRawAttr(xml, name) ?: return null
        return value.toIntOrNull()
    }

    private fun extractFloatAttr(xml: String, name: String): Float? {
        val value = extractRawAttr(xml, name) ?: return null
        return value.toDoubleOrNull()?.toFloat()
    }

    private fun extractStringAttr(xml: String, name: String): String? {
        val value = extractRawAttr(xml, name) ?: return null
        return value.ifEmpty { null }
    }

    private fun extractRawAttr(xml: String, name: String): String? {
        val marker = "$name=\""
        val start = xml.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val end = xml.indexOf('"', valueStart)
        if (end < 0) return null
        return xml.substring(valueStart, end)
    }

    // MARK: - CDATA Extraction

    private fun extractCdata(xml: String): String? {
        val parts = mutableListOf<String>()
        var searchStart = 0
        while (true) {
            val cdataStart = xml.indexOf("<![CDATA[", searchStart)
            if (cdataStart < 0) break
            val contentStart = cdataStart + "<![CDATA[".length
            val cdataEnd = xml.indexOf("]]>", contentStart)
            if (cdataEnd < 0) break
            parts.add(xml.substring(contentStart, cdataEnd))
            searchStart = cdataEnd + "]]>".length
        }
        return if (parts.isEmpty()) null else parts.joinToString("\n")
    }

    // MARK: - RTF Parsing (biçimlendirmeli)

    /** RTF gövdesinde metin içermeyen, atlanması gereken hedef (destination) grupları. */
    private val rtfSkipDestinations = setOf(
        "fonttbl", "colortbl", "stylesheet", "info", "generator", "pict",
        "footer", "header", "footnote", "themedata", "colorschememapping",
        "latentstyles", "listtable", "listoverridetable", "rsid", "xmlnstbl",
        "datastore", "companyname", "operator", "creatim", "revtim",
    )

    private data class RtfCharState(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val fontSize: Float = 12f,
    )

    /**
     * stripRtfFormatting'in (yalnızca düz metin üreten) yerini alır: RTF kontrol sözcüklerini
     * (`\b`, `\i`, `\ul`, `\fsN`, `\par`) tek geçişte izleyerek hem düz metni hem de UYAP paragraf
     * modeliyle aynı [UyapParagraph]/[UyapTextRun] yapısını üretir — böylece RTF içeriği de UYAP
     * içeriğiyle aynı yoldan (PdfConverter/WordConverter/önizleme) zengin biçimlendirmeyle
     * gösterilebilir. Eski sürüm control word'leri metinden SİLMEDEN önce pozisyonlarını kaybediyordu;
     * bu yüzden offset tabanlı run üretimi mümkün değildi — bu yeniden yazım tek geçişte ilerler.
     *
     * Kapsam dışı bırakılan uç durumlar (bilinçli sadeleştirme): `\uN` Unicode kaçışları (nadir;
     * `\'XX` onaltılık kaçışlar zaten destekleniyor) ve `\*` ile işaretlenmemiş tanınmayan hedef
     * grupları (yalnızca yukarıdaki bilinen liste + `\*` öneki atlanır).
     */
    private fun parseRtfFormatted(rtf: String): Pair<String, List<UyapParagraph>> {
        val body = rtf.indexOf("{\\rtf").let { if (it >= 0) rtf.substring(it) else rtf }

        val plainText = StringBuilder()
        val paragraphs = mutableListOf<UyapParagraph>()
        var currentRuns = mutableListOf<UyapTextRun>()

        var state = RtfCharState()
        var runStartState = state
        var runStart = 0

        var groupDepth = 0
        var skipDepth = -1
        val stateStack = ArrayDeque<RtfCharState>()

        fun flushRun(end: Int) {
            if (end > runStart) {
                currentRuns.add(
                    UyapTextRun(
                        startOffset = runStart,
                        length = end - runStart,
                        bold = runStartState.bold,
                        underline = runStartState.underline,
                        italic = runStartState.italic,
                        fontSize = runStartState.fontSize,
                        fontFamily = null,
                    ),
                )
            }
            runStart = end
        }

        fun flushParagraph() {
            flushRun(plainText.length)
            if (currentRuns.isNotEmpty()) {
                paragraphs.add(
                    UyapParagraph(
                        alignment = 0, spaceAbove = 0f, spaceBelow = 0f,
                        leftIndent = 0f, rightIndent = 0f, firstLineIndent = 0f,
                        hangingIndent = 0f, lineSpacing = 0f, tabStops = emptyList(),
                        runs = currentRuns,
                    ),
                )
            }
            currentRuns = mutableListOf()
        }

        fun appendChar(c: Char) {
            if (state != runStartState) {
                flushRun(plainText.length)
                runStartState = state
            }
            plainText.append(c)
        }

        var i = 0
        while (i < body.length) {
            val ch = body[i]
            when {
                ch == '{' -> {
                    groupDepth++
                    stateStack.addLast(state)
                    i++
                }
                ch == '}' -> {
                    if (groupDepth == skipDepth) skipDepth = -1
                    groupDepth--
                    if (stateStack.isNotEmpty()) state = stateStack.removeLast()
                    i++
                }
                ch == '\\' && i + 1 < body.length && body[i + 1] == '*' -> {
                    if (skipDepth < 0) skipDepth = groupDepth
                    i += 2
                }
                ch == '\\' && i + 1 < body.length && body[i + 1] == '\'' -> {
                    val hexStart = i + 2
                    val hexEnd = (hexStart + 2).coerceAtMost(body.length)
                    if (skipDepth < 0 && hexEnd > hexStart) {
                        val code = body.substring(hexStart, hexEnd).toIntOrNull(16)
                        if (code != null) {
                            val charset = WINDOWS_1254 ?: Charsets.ISO_8859_1
                            appendChar(String(byteArrayOf(code.toByte()), charset)[0])
                        }
                    }
                    i = hexEnd
                }
                ch == '\\' && i + 1 < body.length && body[i + 1].isLetter() -> {
                    var j = i + 1
                    while (j < body.length && body[j].isLetter()) j++
                    val word = body.substring(i + 1, j)
                    var k = j
                    var negative = false
                    if (k < body.length && body[k] == '-') { negative = true; k++ }
                    val numStart = k
                    while (k < body.length && body[k].isDigit()) k++
                    val numValue = if (k > numStart) {
                        body.substring(numStart, k).toIntOrNull()?.let { if (negative) -it else it }
                    } else {
                        null
                    }
                    if (k < body.length && body[k] == ' ') k++

                    if (skipDepth < 0) {
                        when {
                            word == "par" -> {
                                flushParagraph()
                                plainText.append('\n')
                                runStart = plainText.length
                            }
                            word == "line" -> appendChar('\n')
                            word == "tab" -> appendChar('\t')
                            word == "b" -> state = state.copy(bold = (numValue ?: 1) != 0)
                            word == "i" -> state = state.copy(italic = (numValue ?: 1) != 0)
                            word == "ulnone" -> state = state.copy(underline = false)
                            word == "ul" -> state = state.copy(underline = (numValue ?: 1) != 0)
                            word.startsWith("ul") -> state = state.copy(underline = true)
                            word == "fs" && numValue != null -> state = state.copy(fontSize = numValue / 2f)
                            word == "plain" -> state = RtfCharState()
                        }
                        if (skipDepth < 0 && word in rtfSkipDestinations) skipDepth = groupDepth
                    }
                    i = k
                }
                ch == '\r' || ch == '\n' -> {
                    // RTF kaynağındaki ham satır sonları yalnızca insan-okunur biçimlendirme
                    // içindir, anlamsızdır — \par/\line dışında metne dahil edilmemeli.
                    i++
                }
                else -> {
                    if (skipDepth < 0) appendChar(ch)
                    i++
                }
            }
        }
        flushParagraph()

        return plainText.toString() to paragraphs
    }

    // MARK: - HTML Stripping

    private val htmlBlockTags = listOf(
        "</p>", "</div>", "</br>", "<br>", "<br/>", "<br />", "</tr>",
        "</h1>", "</h2>", "</h3>", "</h4>", "</li>",
    )

    private val htmlEntities = listOf(
        "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&apos;" to "'", "&#39;" to "'",
        "&nbsp;" to " ", "&ouml;" to "ö", "&uuml;" to "ü",
        "&ccedil;" to "ç", "&Ouml;" to "Ö", "&Uuml;" to "Ü",
        "&Ccedil;" to "Ç", "&#304;" to "İ", "&#305;" to "ı",
        "&#351;" to "ş", "&#350;" to "Ş", "&#287;" to "ğ", "&#286;" to "Ğ",
    )

    private fun stripHtmlTags(html: String): String {
        var result = html
        for (tag in htmlBlockTags) {
            result = result.replace(tag, "\n", ignoreCase = true)
        }
        result = result.replace("</td>", "\t", ignoreCase = true)

        val sb = StringBuilder()
        var i = 0
        while (i < result.length) {
            if (result[i] == '<') {
                val close = result.indexOf('>', i)
                if (close < 0) {
                    sb.append(result, i, result.length)
                    break
                }
                i = close + 1
            } else {
                sb.append(result[i])
                i++
            }
        }
        result = sb.toString()

        for ((entity, char) in htmlEntities) {
            result = result.replace(entity, char)
        }

        return collapseBlankLines(result)
    }

    // MARK: - HTML Table Extraction

    private fun extractHtmlTables(html: String): List<UdfTable> {
        val tables = mutableListOf<UdfTable>()
        var searchFrom = 0

        while (true) {
            val tableStart = html.indexOf("<table", searchFrom, ignoreCase = true)
            if (tableStart < 0) break
            val tableEndTag = html.indexOf("</table>", tableStart, ignoreCase = true)
            if (tableEndTag < 0) break
            val tableEnd = tableEndTag + "</table>".length
            val tableContent = html.substring(tableStart, tableEnd)

            val rows = mutableListOf<List<String>>()
            var rowSearch = 0
            while (true) {
                val trStart = tableContent.indexOf("<tr", rowSearch, ignoreCase = true)
                if (trStart < 0) break
                val trEndTag = tableContent.indexOf("</tr>", trStart, ignoreCase = true)
                if (trEndTag < 0) break
                val trEnd = trEndTag + "</tr>".length
                val rowContent = tableContent.substring(trStart, trEnd)

                val cells = mutableListOf<String>()
                // Not: iOS kaynağı "<td" bulunamazsa "<th"e düşer (range(of:) ?? range(of:)) — bu,
                // aynı satırda <th> hücreleri <td>'den önce geçtiğinde başlıkları atlayabilen bir
                // kusurdur. Burada bilinçli olarak düzeltildi: hangisi önce geliyorsa o kullanılır.
                var cellSearch = 0
                while (true) {
                    val tdStartTd = rowContent.indexOf("<td", cellSearch, ignoreCase = true)
                    val tdStartTh = rowContent.indexOf("<th", cellSearch, ignoreCase = true)
                    val tdStart = listOf(tdStartTd, tdStartTh).filter { it >= 0 }.minOrNull() ?: -1
                    if (tdStart < 0) break

                    val tdCloseTag = if (tdStart == tdStartTd) "</td>" else "</th>"
                    val tdEndTag = rowContent.indexOf(tdCloseTag, tdStart, ignoreCase = true)
                    if (tdEndTag < 0) break

                    val contentStart = rowContent.indexOf('>', tdStart) + 1
                    val cellContent = rowContent.substring(contentStart, tdEndTag)
                    cells.add(stripHtmlTags(cellContent).trim())
                    cellSearch = tdEndTag + tdCloseTag.length
                }
                if (cells.isNotEmpty()) rows.add(cells)
                rowSearch = trEnd
            }
            if (rows.isNotEmpty()) tables.add(UdfTable(rows = rows))
            searchFrom = tableEnd
        }

        return tables
    }

    // MARK: - Section Extraction (fallback)

    private fun extractSections(text: String): List<UdfSection> {
        val lines = text.split("\n")
        val sections = mutableListOf<UdfSection>()
        var currentBody = StringBuilder()
        var currentTitle: String? = null

        fun isTitle(trimmed: String): Boolean {
            if (trimmed.length <= 2 || trimmed.length >= 100) return false
            val allCapsWithLetter = trimmed == trimmed.uppercase(Locale.ROOT) && trimmed.any { it.isLetter() }
            return allCapsWithLetter || trimmed.endsWith(":")
        }

        for (line in lines) {
            val trimmed = line.trim()
            val title = isTitle(trimmed)
            when {
                title && currentBody.toString().trim().isNotEmpty() -> {
                    sections.add(UdfSection(title = currentTitle, body = currentBody.toString().trim(), level = 0))
                    currentBody = StringBuilder()
                    currentTitle = trimmed
                }
                title -> currentTitle = trimmed
                else -> currentBody.append(line).append('\n')
            }
        }
        if (currentBody.toString().trim().isNotEmpty()) {
            sections.add(UdfSection(title = currentTitle, body = currentBody.toString().trim(), level = 0))
        }
        if (sections.isEmpty()) sections.add(UdfSection(title = null, body = text, level = 0))
        return sections
    }

    // MARK: - Metadata

    private fun parseMetadata(entry: UdfZip.Entry): UdfMetadata {
        val text = decodeBestEffort(entry.data)
            ?: return UdfMetadata(author = null, creationDate = null, title = null, subject = null)

        fun extractValue(tag: String): String? {
            val start = text.indexOf("<$tag>")
            if (start < 0) return null
            val contentStart = start + "<$tag>".length
            val end = text.indexOf("</$tag>", contentStart)
            if (end < 0) return null
            return text.substring(contentStart, end).trim()
        }

        fun extractEntry(key: String): String? {
            val marker = "key=\"$key\">"
            val start = text.indexOf(marker)
            if (start < 0) return null
            val contentStart = start + marker.length
            val end = text.indexOf("</entry>", contentStart)
            if (end < 0) return null
            val value = text.substring(contentStart, end).trim()
            return value.ifEmpty { null }
        }

        return UdfMetadata(
            author = extractValue("author") ?: extractValue("Author") ?: extractEntry("uyapsicil"),
            creationDate = extractValue("date") ?: extractValue("Date") ?: extractEntry("tarih"),
            title = extractValue("title") ?: extractValue("Title"),
            subject = extractValue("subject") ?: extractEntry("uyapdogrulamakodu"),
        )
    }

    // MARK: - Encoding

    /**
     * UDF içerikleri genellikle UTF-8'dir, ancak bazı üreticiler Windows-1254 (Türkçe) veya
     * ISO-8859-1 kullanabilir. Sırayla dener; ilk ikisi tek bayt kodlamaları olduğundan pratikte
     * hiç başarısız olmaz — bu yüzden ISO-8859-1 gerçek bir "son çare" görevi görür.
     */
    private fun decodeBestEffort(bytes: ByteArray): String? {
        strictDecode(Charsets.UTF_8, bytes)?.let { return it }
        WINDOWS_1254?.let { cs -> strictDecode(cs, bytes)?.let { return it } }
        strictDecode(Charsets.ISO_8859_1, bytes)?.let { return it }
        return null
    }

    private fun strictDecode(charset: Charset, bytes: ByteArray): String? {
        return try {
            val decoder: CharsetDecoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun collapseBlankLines(text: String): String {
        val lines = text.split("\n").map { it.trim() }
        val result = mutableListOf<String>()
        var lastWasEmpty = false
        for (line in lines) {
            if (line.isEmpty()) {
                if (!lastWasEmpty) result.add("")
                lastWasEmpty = true
            } else {
                result.add(line)
                lastWasEmpty = false
            }
        }
        return result.joinToString("\n").trim()
    }
}
