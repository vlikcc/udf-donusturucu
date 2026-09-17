package com.velikececi.udfdonusturucu.converter

import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object UdfParser {

    data class ParsedUdf(
        val text: String,
        val title: String = ""
    )

    fun parse(file: File): ParsedUdf {
        return try {
            // UDF dosyaları genellikle içi XML içeren ZIP sıkıştırmalı dosyalardır
            ZipFile(file).use { zip ->
                val contentEntry = zip.getEntry("content.xml") ?: zip.entries().asSequence().firstOrNull { it.name.endsWith(".xml") }
                if (contentEntry != null) {
                    zip.getInputStream(contentEntry).use { stream ->
                        parseXmlContent(stream)
                    }
                } else {
                    // Düz XML olarak dene
                    parseXmlDirectly(file)
                }
            }
        } catch (e: Exception) {
            // ZIP değilse doğrudan XML olarak oku
            parseXmlDirectly(file)
        }
    }

    private fun parseXmlDirectly(file: File): ParsedUdf {
        val bytes = file.readBytes()
        val textContent = detectAndDecode(bytes)
        return parseXmlString(textContent)
    }

    private fun parseXmlContent(inputStream: InputStream): ParsedUdf {
        val bytes = inputStream.readBytes()
        val textContent = detectAndDecode(bytes)
        return parseXmlString(textContent)
    }

    private fun detectAndDecode(bytes: ByteArray): String {
        val preview = String(bytes, 0, minOf(bytes.size, 500), Charset.forName("ISO-8859-1"))
        val encoding = if (preview.contains("encoding=\"windows-1254\"", ignoreCase = true) ||
            preview.contains("encoding='windows-1254'", ignoreCase = true)) {
            Charset.forName("windows-1254")
        } else if (preview.contains("encoding=\"ISO-8859-9\"", ignoreCase = true) ||
            preview.contains("encoding='ISO-8859-9'", ignoreCase = true)) {
            Charset.forName("ISO-8859-9")
        } else {
            Charsets.UTF_8
        }
        return String(bytes, encoding)
    }

    private fun parseXmlString(xml: String): ParsedUdf {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        return try {
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(xml.byteInputStream(Charsets.UTF_8))
            val textNodes = doc.getElementsByTagName("content")
            val sb = StringBuilder()
            for (i in 0 until textNodes.length) {
                val node = textNodes.item(i)
                sb.append(node.textContent).append("\n")
            }
            val result = if (sb.isNotEmpty()) sb.toString().trim() else doc.documentElement.textContent.trim()
            ParsedUdf(text = result)
        } catch (e: Exception) {
            // XML parse başarısızsa regex ile temiz metin çıkar
            val clean = xml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
            ParsedUdf(text = clean)
        }
    }
}
