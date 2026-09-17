package com.velikececi.udfdonusturucu.core.converters

import com.velikececi.udfdonusturucu.core.model.ExtractedContent
import com.velikececi.udfdonusturucu.core.model.ExtractedParagraph
import com.velikececi.udfdonusturucu.core.model.ExtractedTextRun
import com.velikececi.udfdonusturucu.core.model.ExtractionException
import com.velikececi.udfdonusturucu.core.zip.UdfZip
import java.io.File
import java.util.Locale

/** DOCXExtractor.swift'in Kotlin karşılığı — DOCX'ten metin ve biçimlendirme çıkarır. */
object DocxExtractor {

    fun extract(file: File): ExtractedContent {
        val entries = UdfZip.extractEntries(file)

        val docEntry = entries.firstOrNull { it.fileName.lowercase(Locale.ROOT).contains("word/document.xml") }
            ?: throw ExtractionException.InvalidFormat()

        val xml = String(docEntry.data, Charsets.UTF_8)

        var defaultFontFamily = "Times New Roman"
        var defaultFontSize = 12f
        entries.firstOrNull { it.fileName.lowercase(Locale.ROOT).contains("word/styles.xml") }?.let { stylesEntry ->
            val stylesXml = String(stylesEntry.data, Charsets.UTF_8)
            val (family, size) = parseDefaultStyles(stylesXml)
            if (family != null) defaultFontFamily = family
            if (size != null) defaultFontSize = size
        }

        val paragraphs = parseDocxParagraphs(xml, defaultFontFamily, defaultFontSize)
        val plainText = paragraphs.joinToString("\n") { it.text }

        if (plainText.trim().isEmpty()) {
            throw ExtractionException.NoTextContent()
        }

        return ExtractedContent(plainText = plainText, paragraphs = paragraphs)
    }

    // MARK: - Default Styles

    private fun parseDefaultStyles(xml: String): Pair<String?, Float?> {
        var family: String? = null
        var size: Float? = null

        val rPrDefaultStart = xml.indexOf("<w:rPrDefault>")
        if (rPrDefaultStart >= 0) {
            val endTag = xml.indexOf("</w:rPrDefault>", rPrDefaultStart)
            val searchEnd = if (endTag >= 0) endTag + "</w:rPrDefault>".length else xml.length
            val section = xml.substring(rPrDefaultStart, searchEnd)

            extractQuoted(section, "w:ascii=\"")?.let { family = it }
            extractQuoted(section, "<w:sz w:val=\"")?.toDoubleOrNull()?.let { size = (it / 2.0).toFloat() }
        }

        return family to size
    }

    // MARK: - Paragraph Parsing

    private fun parseDocxParagraphs(xml: String, defaultFamily: String, defaultSize: Float): List<ExtractedParagraph> {
        val paragraphs = mutableListOf<ExtractedParagraph>()
        var searchStart = 0

        while (true) {
            val pStart = xml.indexOf("<w:p", searchStart)
            if (pStart < 0) break
            val pEndTag = xml.indexOf("</w:p>", pStart)
            if (pEndTag < 0) break
            val pEnd = pEndTag + "</w:p>".length

            val paraXml = xml.substring(pStart, pEnd)

            val alignment = extractAlignment(paraXml)
            val runs = extractRuns(paraXml, defaultFamily, defaultSize)

            paragraphs.add(ExtractedParagraph(runs = runs, alignment = alignment))
            searchStart = pEnd
        }

        return paragraphs
    }

    private fun extractRuns(paraXml: String, defaultFamily: String, defaultSize: Float): List<ExtractedTextRun> {
        val runs = mutableListOf<ExtractedTextRun>()
        var runSearch = 0

        while (true) {
            val rStartPlain = paraXml.indexOf("<w:r>", runSearch)
            val rStartAttr = paraXml.indexOf("<w:r ", runSearch)
            val rStart = listOf(rStartPlain, rStartAttr).filter { it >= 0 }.minOrNull() ?: -1
            if (rStart < 0) break

            val rEndTag = paraXml.indexOf("</w:r>", rStart)
            if (rEndTag < 0) break
            val rEnd = rEndTag + "</w:r>".length

            val runXml = paraXml.substring(rStart, rEnd)

            val isBold = runXml.contains("<w:b/>") || runXml.contains("<w:b ") ||
                (runXml.contains("<w:b>") && !runXml.contains("<w:b w:val=\"false\"") && !runXml.contains("<w:b w:val=\"0\""))
            val isItalic = runXml.contains("<w:i/>") || runXml.contains("<w:i ") ||
                (runXml.contains("<w:i>") && !runXml.contains("<w:i w:val=\"false\"") && !runXml.contains("<w:i w:val=\"0\""))
            val isUnderline = runXml.contains("<w:u ") && !runXml.contains("w:val=\"none\"")

            var fontSize = defaultSize
            extractQuoted(runXml, "<w:sz w:val=\"")?.toDoubleOrNull()?.let { fontSize = (it / 2.0).toFloat() }

            var fontFamily = defaultFamily
            extractQuoted(runXml, "w:ascii=\"")?.let { fontFamily = it }

            var runText = ""
            var textSearch = 0
            while (true) {
                val tStart = runXml.indexOf("<w:t", textSearch)
                if (tStart < 0) break
                val tagClose = runXml.indexOf('>', tStart)
                if (tagClose < 0) break
                val tEndTag = runXml.indexOf("</w:t>", tagClose)
                if (tEndTag < 0) break
                runText += runXml.substring(tagClose + 1, tEndTag)
                textSearch = tEndTag + "</w:t>".length
            }

            if (runXml.contains("<w:tab/>")) {
                runText += "\t"
            }

            if (runText.isNotEmpty()) {
                runs.add(
                    ExtractedTextRun(
                        text = runText,
                        isBold = isBold,
                        isItalic = isItalic,
                        isUnderline = isUnderline,
                        fontSize = fontSize,
                        fontFamily = fontFamily,
                    ),
                )
            }

            runSearch = rEnd
        }

        if (runs.isEmpty()) {
            runs.add(
                ExtractedTextRun(
                    text = "",
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    fontSize = defaultSize,
                    fontFamily = defaultFamily,
                ),
            )
        }

        return runs
    }

    private fun extractAlignment(paraXml: String): Int {
        val value = extractQuoted(paraXml, "<w:jc w:val=\"") ?: return 0
        return when (value) {
            "center" -> 1
            "right" -> 2
            "both" -> 3
            else -> 0
        }
    }

    private fun extractQuoted(xml: String, marker: String): String? {
        val start = xml.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val end = xml.indexOf('"', valueStart)
        if (end < 0) return null
        return xml.substring(valueStart, end)
    }
}
