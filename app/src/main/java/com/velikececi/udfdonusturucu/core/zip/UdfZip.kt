package com.velikececi.udfdonusturucu.core.zip

import com.velikececi.udfdonusturucu.core.model.UdfParserException
import java.io.File
import java.util.zip.ZipFile

/**
 * ZIPExtractor.swift'in Kotlin karşılığı. iOS'ta Compression çerçevesi üzerinden elle
 * yazılmış merkezi dizin/deflate ayrıştırması gerekiyordu; Android'de `java.util.zip` bunu
 * doğrudan (stored + deflate, CRC32 dahil) sağladığı için sarmalayıcı çok daha ince.
 */
object UdfZip {

    data class Entry(val fileName: String, val data: ByteArray)

    fun extractEntries(file: File): List<Entry> {
        val entries = mutableListOf<Entry>()
        try {
            ZipFile(file).use { zip ->
                val zipEntries = zip.entries()
                while (zipEntries.hasMoreElements()) {
                    val zipEntry = zipEntries.nextElement()
                    if (zipEntry.isDirectory) continue
                    val bytes = zip.getInputStream(zipEntry).use { it.readBytes() }
                    entries.add(Entry(fileName = zipEntry.name, data = bytes))
                }
            }
        } catch (e: java.util.zip.ZipException) {
            throw UdfParserException.InvalidZipArchive()
        }

        if (entries.isEmpty()) {
            throw UdfParserException.InvalidZipArchive()
        }
        return entries
    }
}
