package com.velikececi.udfdonusturucu.ui.conversion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.data.ConversionDirection
import com.velikececi.udfdonusturucu.data.ConversionOutcome
import com.velikececi.udfdonusturucu.data.ConversionProgress
import com.velikececi.udfdonusturucu.data.ConversionRepository
import com.velikececi.udfdonusturucu.data.OutputFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * Ana ekran (dosya seçimi) → Dönüşüm ekranı (ilerleme) → Sonuç ekranı arasında paylaşılan durum.
 * `AppNavHost` düzeyinde tek örnek oluşturulur ve üç ekrana da parametre olarak geçirilir —
 * navigasyon argümanlarıyla büyük dosya listeleri taşımak yerine.
 */
class ConversionFlowViewModel(private val conversionRepository: ConversionRepository) : ViewModel() {

    data class FlowState(
        val files: List<File> = emptyList(),
        val direction: ConversionDirection = ConversionDirection.UDF_TO_OTHER,
        val outputFormat: OutputFormat = OutputFormat.PDF,
        val isRunning: Boolean = false,
        val progress: ConversionProgress? = null,
        val outcomes: List<ConversionOutcome> = emptyList(),
    )

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state

    fun setDirection(direction: ConversionDirection) {
        _state.update { it.copy(direction = direction, files = emptyList(), outcomes = emptyList()) }
    }

    fun setOutputFormat(format: OutputFormat) {
        _state.update { it.copy(outputFormat = format) }
    }

    fun setSelectedFiles(files: List<File>) {
        _state.update { it.copy(files = files, outcomes = emptyList()) }
    }

    fun removeFile(file: File) {
        _state.update { it.copy(files = it.files - file) }
    }

    /** Dönüşümü başlatır; sonuçlar hazır olduğunda [state].outcomes dolar. Zaten çalışıyorsa yok sayılır. */
    fun startConversion() {
        val current = _state.value
        if (current.files.isEmpty() || current.isRunning) return

        _state.update { it.copy(isRunning = true, outcomes = emptyList(), progress = null) }
        viewModelScope.launch {
            val outcomes = conversionRepository.convert(
                files = current.files,
                direction = current.direction,
                outputFormat = current.outputFormat,
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
            )
            _state.update { it.copy(outcomes = outcomes, isRunning = false, progress = null) }
        }
    }

    /** Sonuç ekranından ana ekrana dönüldüğünde seçim/sonuçları temizler (yön ve format korunur). */
    fun resetForNewSelection() {
        _state.update { FlowState(direction = it.direction, outputFormat = it.outputFormat) }
    }
}
