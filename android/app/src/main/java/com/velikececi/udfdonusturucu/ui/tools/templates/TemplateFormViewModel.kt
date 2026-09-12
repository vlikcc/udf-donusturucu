package com.velikececi.udfdonusturucu.ui.tools.templates

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.PetitionTemplate
import com.velikececi.udfdonusturucu.core.tools.TemplateEngine
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TemplateFormUiState(
    val values: Map<String, String> = emptyMap(),
    val isWorking: Boolean = false,
    val resultFile: File? = null,
    val errorMessage: String? = null,
)

/** TemplateFormView.swift'in durum yönetimi karşılığı — alan değerleri + UDF/PDF üretimi. */
class TemplateFormViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    val template: PetitionTemplate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TemplateFormUiState())
    val uiState: StateFlow<TemplateFormUiState> = _uiState

    fun setValue(key: String, value: String) {
        _uiState.update { it.copy(values = it.values + (key to value), resultFile = null) }
    }

    fun createUdf() = create(formatLabel = "UDF") { TemplateEngine.createUdf(template, _uiState.value.values, context) }

    fun createPdf() = create(formatLabel = "PDF") { TemplateEngine.createPdf(template, _uiState.value.values, context) }

    private fun create(formatLabel: String, block: () -> File) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { block() }
                historyRepository.addRecord(
                    ConversionRecord.create(
                        originalFileName = result.name,
                        outputFormat = formatLabel,
                        success = true,
                        outputPath = result.absolutePath,
                    ),
                )
                _uiState.update { it.copy(isWorking = false, resultFile = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isWorking = false, errorMessage = "Oluşturma başarısız: ${e.message}") }
            }
        }
    }
}
