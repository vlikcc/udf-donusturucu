package com.velikececi.udfdonusturucu.core.tools

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.velikececi.udfdonusturucu.core.converters.OutputPaths
import com.velikececi.udfdonusturucu.core.converters.PdfBoxInit
import com.velikececi.udfdonusturucu.core.converters.PdfConverter
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class MergeException(message: String) : Exception(message) {
    class CannotOpenFile(name: String) : MergeException("\"$name\" dosyası açılamadı.")
    class NoPages : MergeException("Birleştirilecek sayfa bulunamadı.")
    class WriteFailed(detail: String?) : MergeException("Birleştirme başarısız: ${detail ?: "bilinmeyen hata"}")
}

/**
 * MergeService.swift'in Kotlin karşılığı. UDF girdiler önce [UdfParser] + [PdfConverter] ile
 * PDF'e çevrilir, ardından tüm PDF'ler `PDFMergerUtility` ile tek dosyada birleştirilir.
 *
 * iOS tarafı sayfa sayfa `PDFDocument.insert(page, at:)` döngüsü kullanabiliyordu (PDFKit COS
 * nesnelerini kendi kopyalıyor); PDFBox'ta bunun karşılığı YOKTUR — `PDDocument.addPage(kaynak
 * sayfa)` yalnızca sayfa sözlüğünü kopyalar, dolaylı referanslar kaynak belgenin COS havuzuna
 * bakmaya devam eder ve çıktı bozuk olur. Bu yüzden kaynak nesneleri doğru klonlayan
 * `PDFMergerUtility.appendDocument` (üstündeki `mergeDocuments`) kullanılır.
 */
object MergeService {

    /**
     * [files] en az 2 eleman içermeli. [onProgress] her girdi işlenmeden hemen önce
     * `(sıraNo, toplam)` ile çağrılır — UDF→PDF ön dönüşümü dahil ilerlemeyi yansıtır.
     */
    fun merge(files: List<File>, context: Context, onProgress: ((current: Int, total: Int) -> Unit)? = null): File {
        require(files.size >= 2) { "En az 2 dosya gerekli." }
        PdfBoxInit.ensure(context)

        val pdfInputs = files.mapIndexed { index, file ->
            onProgress?.invoke(index + 1, files.size)
            when (file.extension.lowercase(Locale.ROOT)) {
                "udf" -> try {
                    val document = UdfParser.parse(file)
                    PdfConverter.convert(document, context)
                } catch (e: Exception) {
                    throw MergeException.CannotOpenFile(file.name)
                }
                else -> file
            }
        }

        val outputFile = File(
            OutputPaths.outputDirectory(context),
            "Birlestirilmis_${SimpleDateFormat("yyyyMMdd_HHmm", Locale("tr")).format(Date())}.pdf",
        )

        try {
            val merger = PDFMergerUtility().apply {
                destinationFileName = outputFile.absolutePath
                acroFormMergeMode = PDFMergerUtility.AcroFormMergeMode.PDFBOX_LEGACY_MODE
                isIgnoreAcroFormErrors = true
            }
            pdfInputs.forEach { merger.addSource(it) }
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir))
        } catch (e: MergeException) {
            throw e
        } catch (e: Exception) {
            throw MergeException.WriteFailed(e.message)
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            throw MergeException.NoPages()
        }

        return outputFile
    }
}
