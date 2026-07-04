package com.velikececi.udfdonusturucu.data

import android.content.Context
import com.velikececi.udfdonusturucu.core.converters.PdfConverter
import com.velikececi.udfdonusturucu.core.converters.UdfCreator
import com.velikececi.udfdonusturucu.core.converters.WordConverter
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * ContentView.swift içindeki dönüşüm orkestrasyonunun (seç → ayrıştır → dönüştür → kaydet)
 * Kotlin karşılığı. Tek giriş noktası [convert] — toplu dönüşümde günlük hak tek seferde
 * ([LimitRepository.useConversion]) düşülür, her dosya sırayla işlenir ve sonucu ne olursa
 * olsun (başarılı/başarısız) [HistoryRepository]'ye kaydedilir.
 */
class ConversionRepository(
    private val context: Context,
    private val limitRepository: LimitRepository,
    private val historyRepository: HistoryRepository,
) {
    suspend fun convert(
        files: List<File>,
        direction: ConversionDirection,
        outputFormat: OutputFormat,
        onProgress: suspend (ConversionProgress) -> Unit = {},
    ): List<ConversionOutcome> = withContext(Dispatchers.Default) {
        if (files.isEmpty()) return@withContext emptyList()

        if (!limitRepository.useConversion(files.size)) {
            return@withContext files.map {
                ConversionOutcome(
                    originalFileName = it.name,
                    success = false,
                    outputFile = null,
                    errorMessage = "Günlük dönüşüm hakkınız doldu.",
                )
            }
        }

        val outcomes = mutableListOf<ConversionOutcome>()
        files.forEachIndexed { index, file ->
            onProgress(ConversionProgress(currentIndex = index + 1, total = files.size, currentFileName = file.name))

            val outcome = convertSingle(file, direction, outputFormat)
            outcomes.add(outcome)

            historyRepository.addRecord(
                ConversionRecord.create(
                    originalFileName = file.name,
                    outputFormat = resultFormatLabel(direction, outputFormat),
                    success = outcome.success,
                    outputPath = outcome.outputFile?.absolutePath,
                ),
            )
        }

        outcomes
    }

    private fun convertSingle(file: File, direction: ConversionDirection, outputFormat: OutputFormat): ConversionOutcome {
        return try {
            val outputFile = when (direction) {
                ConversionDirection.UDF_TO_OTHER -> {
                    val document = UdfParser.parse(file)
                    when (outputFormat) {
                        OutputFormat.PDF -> PdfConverter.convert(document, context)
                        OutputFormat.DOCX -> WordConverter.convert(document, context)
                    }
                }
                ConversionDirection.OTHER_TO_UDF -> when (file.extension.lowercase(Locale.ROOT)) {
                    "pdf" -> UdfCreator.createFromPdf(file, context)
                    "docx" -> UdfCreator.createFromDocx(file, context)
                    else -> throw IllegalArgumentException("Desteklenmeyen dosya türü: .${file.extension}")
                }
            }
            ConversionOutcome(originalFileName = file.name, success = true, outputFile = outputFile, errorMessage = null)
        } catch (e: Throwable) {
            // Throwable (Exception değil): çok büyük/bozuk bir dosyanın OutOfMemoryError gibi bir
            // Error fırlatması tüm toplu işlemi çökertmemeli — yalnızca o dosya başarısız sayılır.
            ConversionOutcome(originalFileName = file.name, success = false, outputFile = null, errorMessage = e.message)
        }
    }

    private fun resultFormatLabel(direction: ConversionDirection, outputFormat: OutputFormat): String =
        when (direction) {
            ConversionDirection.UDF_TO_OTHER -> outputFormat.name
            ConversionDirection.OTHER_TO_UDF -> "UDF"
        }
}
