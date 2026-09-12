package com.velikececi.udfdonusturucu.core.tools.editor

import android.content.Context
import com.velikececi.udfdonusturucu.core.converters.UdfCreator
import com.velikececi.udfdonusturucu.core.model.UdfContentType
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import com.velikececi.udfdonusturucu.core.tools.SignatureInspector
import java.io.File

data class UdfEditableDocument(
    val sourceFile: File,
    val baseName: String,
    val model: UdfEditDocument,
    val hasSignature: Boolean,
)

sealed class UdfEditorException(message: String) : Exception(message) {
    class EmptyContent : UdfEditorException("Belge boş olamaz.")
    class SaveFailed(detail: String) : UdfEditorException("UDF kaydedilemedi: $detail")
}

/**
 * UDFEditorService.swift'in Kotlin karşılığı — UDF dosyasını yapılandırılmış model olarak
 * yükler; tablo, alan ve renk bilgisini korur. iOS'un `prepareReadableCopy` (security-scoped
 * resource kopyalama) adımının Android karşılığı yoktur — çağıran taraf zaten
 * [com.velikececi.udfdonusturucu.data.FileCopier] ile önbelleğe kopyalanmış bir [File] verir.
 */
object UdfEditorService {

    fun load(file: File): UdfEditableDocument {
        val document = UdfParser.parse(file)
        val model = if (document.content.contentType == UdfContentType.UYAP) {
            UdfStructureParser.parse(document.content.rawContent, document.content.text)
        } else {
            UdfStructureParser.parse("", document.content.text)
        }

        if (model.plainText().isBlank()) {
            throw UdfEditorException.EmptyContent()
        }

        val hasSignature = try {
            SignatureInspector.inspect(file).hasSignature
        } catch (e: Exception) {
            false
        }

        return UdfEditableDocument(
            sourceFile = file,
            baseName = file.nameWithoutExtension,
            model = model,
            hasSignature = hasSignature,
        )
    }

    fun save(model: UdfEditDocument, baseName: String, context: Context): File {
        if (model.plainText().isBlank()) {
            throw UdfEditorException.EmptyContent()
        }
        return try {
            UdfCreator.create("Duzenlenmis_$baseName", model, context)
        } catch (e: Exception) {
            throw UdfEditorException.SaveFailed(e.message ?: "bilinmeyen hata")
        }
    }

    /** Doküman "kirli mi?" (kaydedilmemiş değişiklik var mı?) kontrolü için ucuz bir özet. */
    fun fingerprint(model: UdfEditDocument): String {
        val built = UdfStructureWriter.build(model)
        return built.cdataText + (built.headersXML ?: "") + built.elementsXML + (built.footersXML ?: "")
    }
}

fun UdfEditDocument.plainText(): String = UdfStructureWriter.build(this).cdataText
