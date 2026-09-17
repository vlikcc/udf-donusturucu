package com.velikececi.udfdonusturucu.ui.tools.ocr

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.OcrException
import com.velikececi.udfdonusturucu.core.tools.OcrService
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class OcrExportFormat { DOCX, PDF, UDF }

data class OcrUiState(
    val selectedFile: File? = null,
    val isImage: Boolean = false,
    val isRecognizing: Boolean = false,
    val progressText: String? = null,
    val recognizedText: String? = null,
    val exportedFile: File? = null,
    val errorMessage: String? = null,
)

/** OCRView.swift'in durum yönetimi karşılığı. */
class OcrViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrUiState())
    val uiState: StateFlow<OcrUiState> = _uiState

    fun selectFile(file: File, isImage: Boolean) {
        _uiState.update { OcrUiState(selectedFile = file, isImage = isImage) }
    }

    fun recognize() {
        val state = _uiState.value
        if (state.isRecognizing) return
        val file = state.selectedFile ?: return

        _uiState.update { it.copy(isRecognizing = true, errorMessage = null, recognizedText = null, exportedFile = null) }
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.Default) {
                    if (state.isImage) {
                        OcrService.recognizeText(Uri.fromFile(file), context)
                    } else {
                        OcrService.recognizeText(file, context) { current, total ->
                            _uiState.update { it.copy(progressText = "Sayfa $current/$total taranıyor...") }
                        }
                    }
                }
                _uiState.update { it.copy(isRecognizing = false, progressText = null, recognizedText = text) }
            } catch (e: OcrException) {
                _uiState.update { it.copy(isRecognizing = false, progressText = null, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecognizing = false, progressText = null, errorMessage = "Metin tanıma başarısız: ${e.message}") }
            }
        }
    }

    fun export(format: OcrExportFormat) {
        val text = _uiState.value.recognizedText ?: return
        val baseName = _uiState.value.selectedFile?.nameWithoutExtension ?: "Belge"

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    when (format) {
                        OcrExportFormat.DOCX -> OcrService.exportDocx(text, baseName, context)
                        OcrExportFormat.PDF -> OcrService.exportPdf(text, baseName, context)
                        OcrExportFormat.UDF -> OcrService.exportUdf(text, baseName, context)
                    }
                }
                historyRepository.addRecord(
                    ConversionRecord.create(
                        originalFileName = result.name,
                        outputFormat = format.name,
                        success = true,
                        outputPath = result.absolutePath,
                    ),
                )
                _uiState.update { it.copy(exportedFile = result, errorMessage = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Dışa aktarma başarısız: ${e.message}") }
            }
        }
    }
}
