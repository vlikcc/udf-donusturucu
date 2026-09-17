package com.velikececi.udfdonusturucu.converter

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object UdfCreator {

    fun createUdf(text: String, outputFile: File) {
        val escapedText = escapeXml(text)
        val contentXml = """<?xml version="1.0" encoding="UTF-8"?>
<template format="udf" version="1.0">
    <header>
        <title>Evrak Dönüştürücü</title>
        <creationDate>${System.currentTimeMillis()}</creationDate>
    </header>
    <content>$escapedText</content>
</template>""".trimIndent()

        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            val entry = ZipEntry("content.xml")
            zos.putNextEntry(entry)
            zos.write(contentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
