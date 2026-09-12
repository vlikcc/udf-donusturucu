package com.velikececi.udfdonusturucu.ui.tools.merge

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.MergeException
import com.velikececi.udfdonusturucu.core.tools.MergeService
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

data class MergeUiState(
    val files: List<File> = emptyList(),
    val isMerging: Boolean = false,
    val resultFile: File? = null,
    val errorMessage: String? = null,
)

/** MergeView.swift'in durum yönetimi karşılığı. */
class MergeViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState

    /** Yeni seçilen dosyalar mevcut listeye eklenir, aynı yoldaki tekrarlar atlanır. */
    fun addFiles(newFiles: List<File>) {
        _uiState.update { state ->
            val merged = (state.files + newFiles).distinctBy { it.absolutePath }
            state.copy(files = merged, resultFile = null, errorMessage = null)
        }
    }

    fun removeFile(file: File) {
        _uiState.update { it.copy(files = it.files - file, resultFile = null) }
    }

    /** iOS `.onMove` (sürükle-sırala) karşılığı — burada yukarı/aşağı taşıma düğmeleriyle. */
    fun moveFile(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.files.toMutableList()
            if (from !in list.indices || to !in list.indices) return@update state
            val item = list.removeAt(from)
            list.add(to, item)
            state.copy(files = list)
        }
    }

    fun merge() {
        val files = _uiState.value.files
        if (files.size < 2 || _uiState.value.isMerging) return

        _uiState.update { it.copy(isMerging = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    if (files.all { it.extension.lowercase(Locale.ROOT) == "udf" }) {
                        UdfToolsService.mergeUdf(files, context)
                    } else {
                        MergeService.merge(files, context)
                    }
                }
                historyRepository.addRecord(
                    ConversionRecord.create(
                        originalFileName = result.name,
                        outputFormat = result.extension.uppercase(Locale.ROOT),
                        success = true,
                        outputPath = result.absolutePath,
                    ),
                )
                _uiState.update { it.copy(isMerging = false, resultFile = result) }
            } catch (e: MergeException) {
                _uiState.update { it.copy(isMerging = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isMerging = false, errorMessage = "Birleştirme başarısız: ${e.message}") }
            }
        }
    }
}
