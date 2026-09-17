package com.velikececi.udfdonusturucu.ui.tools.compress

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.CompressionQuality
import com.velikececi.udfdonusturucu.core.tools.PdfToolsException
import com.velikececi.udfdonusturucu.core.tools.PdfToolsService
import com.velikececi.udfdonusturucu.core.tools.UdfToolsException
import com.velikececi.udfdonusturucu.core.tools.UdfToolsService
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class CompressionDisplayResult(
    val outputFile: File,
    val originalBytes: Long,
    val compressedBytes: Long,
)

data class CompressUiState(
    val file: File? = null,
    val quality: CompressionQuality = CompressionQuality.BALANCED,
    val isCompressing: Boolean = false,
    val result: CompressionDisplayResult? = null,
    val errorMessage: String? = null,
) {
    val didNotShrink: Boolean get() = result != null && result.compressedBytes >= result.originalBytes
    val savingsPercent: Int?
        get() = result?.takeIf { it.originalBytes > 0 && !didNotShrink }?.let {
            (100 - (it.compressedBytes * 100 / it.originalBytes)).toInt()
        }
}

/** CompressView.swift'in durum yönetimi karşılığı. PDF ve UDF girdilerini ayırır. */
class CompressViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState

    fun setFile(file: File) {
        _uiState.update { it.copy(file = file, result = null, errorMessage = null) }
    }

    fun setQuality(quality: CompressionQuality) {
        _uiState.update { it.copy(quality = quality) }
    }

    fun compress() {
        val state = _uiState.value
        val file = state.file ?: return
        if (state.isCompressing) return

        _uiState.update { it.copy(isCompressing = true, errorMessage = null, result = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (file.extension.lowercase(Locale.ROOT) == "udf") {
                        UdfToolsService.compress(file, context).let {
                            CompressionDisplayResult(it.outputFile, it.originalBytes, it.compressedBytes)
                        }
                    } else {
                        PdfToolsService.compress(file, state.quality, context).let {
                            CompressionDisplayResult(it.outputFile, it.originalBytes, it.compressedBytes)
                        }
                    }
                }
                historyRepository.addRecord(
                    ConversionRecord.create(
                        originalFileName = result.outputFile.name,
                        outputFormat = result.outputFile.extension.uppercase(Locale.ROOT),
                        success = true,
                        outputPath = result.outputFile.absolutePath,
                    ),
                )
                _uiState.update { it.copy(isCompressing = false, result = result) }
            } catch (e: PdfToolsException) {
                _uiState.update { it.copy(isCompressing = false, errorMessage = e.message) }
            } catch (e: UdfToolsException) {
                _uiState.update { it.copy(isCompressing = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCompressing = false, errorMessage = "Sıkıştırma başarısız: ${e.message}") }
            }
        }
    }
}
