package com.velikececi.udfdonusturucu.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.UdfApp
import com.velikececi.udfdonusturucu.converter.*
import com.velikececi.udfdonusturucu.data.ConversionHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as UdfApp
    private val preferencesRepository = app.preferencesRepository
    private val database = app.database

    val isPremium: StateFlow<Boolean> = preferencesRepository.isPremium
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val remainingConversions: StateFlow<Int> = preferencesRepository.remainingConversions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val conversionHistory: StateFlow<List<ConversionHistory>> = database.conversionDao().getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.checkAndResetDailyLimit()
        }
    }

    fun resetConversionState() {
        _conversionState.value = ConversionState.Idle
    }

    fun convertFile(uri: Uri, direction: ConversionDirection, onNeedPaywall: () -> Unit) {
        viewModelScope.launch {
            val canConvert = preferencesRepository.useConversion()
            if (!canConvert) {
                onNeedPaywall()
                return@launch
            }

            _conversionState.value = ConversionState.Processing(0.1f, "Dosya okunuyor...")

            try {
                val context = app.applicationContext
                val sourceFileName = getFileNameFromUri(uri) ?: "belge"
                val tempInputFile = withContext(Dispatchers.IO) {
                    val temp = File(context.cacheDir, "input_${System.currentTimeMillis()}_$sourceFileName")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    temp
                }

                _conversionState.value = ConversionState.Processing(0.4f, "İçerik ayrıştırılıyor...")

                val outputExtension = when (direction) {
                    ConversionDirection.UDF_TO_PDF -> "pdf"
                    ConversionDirection.UDF_TO_DOCX -> "docx"
                    ConversionDirection.PDF_TO_UDF -> "udf"
                    ConversionDirection.DOCX_TO_UDF -> "udf"
                }

                val baseName = sourceFileName.substringBeforeLast(".")
                val outputFileName = "$baseName.$outputExtension"
                val outputDir = File(context.filesDir, "conversions").apply { mkdirs() }
                val outputFile = File(outputDir, outputFileName)

                withContext(Dispatchers.IO) {
                    when (direction) {
                        ConversionDirection.UDF_TO_PDF -> {
                            val parsed = UdfParser.parse(tempInputFile)
                            PdfConverter.createPdfFromText(parsed.text, outputFile)
                        }
                        ConversionDirection.UDF_TO_DOCX -> {
                            val parsed = UdfParser.parse(tempInputFile)
                            WordConverter.createDocxFromText(parsed.text, outputFile)
                        }
                        ConversionDirection.PDF_TO_UDF -> {
                            val text = PdfConverter.extractTextFromPdf(tempInputFile)
                            UdfCreator.createUdf(text, outputFile)
                        }
                        ConversionDirection.DOCX_TO_UDF -> {
                            val text = DocxExtractor.extractText(tempInputFile)
                            UdfCreator.createUdf(text, outputFile)
                        }
                    }

                    // Geçmişe kaydet
                    database.conversionDao().insert(
                        ConversionHistory(
                            sourceFileName = sourceFileName,
                            outputFileName = outputFileName,
                            sourceFilePath = tempInputFile.absolutePath,
                            outputFilePath = outputFile.absolutePath,
                            conversionType = direction.title,
                            fileSize = outputFile.length()
                        )
                    )
                }

                _conversionState.value = ConversionState.Success(outputFile)
            } catch (e: Exception) {
                _conversionState.value = ConversionState.Error(e.localizedMessage ?: "Dönüştürme sırasında bir hata oluştu.")
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        val cursor = app.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    fun deleteHistoryItem(item: ConversionHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            database.conversionDao().delete(item)
            try {
                File(item.outputFilePath).delete()
            } catch (_: Exception) {}
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.conversionDao().clearAll()
        }
    }
}
