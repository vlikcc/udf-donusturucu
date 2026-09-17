package com.velikececi.udfdonusturucu.ui.tools.encrypt

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class EncryptUiState(
    val file: File? = null,
    val password: String = "",
    val passwordConfirm: String = "",
    val isEncrypting: Boolean = false,
    val resultFile: File? = null,
    val errorMessage: String? = null,
) {
    val isPasswordTooShort: Boolean get() = password.isNotEmpty() && password.length < 4
    val passwordsMismatch: Boolean get() = passwordConfirm.isNotEmpty() && password != passwordConfirm
    val canEncrypt: Boolean
        get() = file != null && password.length >= 4 && password == passwordConfirm && !isEncrypting
    val isUdf: Boolean get() = file?.extension?.lowercase(Locale.ROOT) == "udf"
    val isEncryptedUdf: Boolean get() = file?.extension?.lowercase(Locale.ROOT) == "udfenc"
}

/** EncryptView.swift'in durum yönetimi karşılığı. PDF, UDF ve UDFENC işlemlerini ayırır. */
class EncryptViewModel(
    private val context: Context,
    private val historyRepository: HistoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EncryptUiState())
    val uiState: StateFlow<EncryptUiState> = _uiState

    fun setFile(file: File) {
        _uiState.update { it.copy(file = file, resultFile = null, errorMessage = null, password = "", passwordConfirm = "") }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value, resultFile = null) }
    }

    fun setPasswordConfirm(value: String) {
        _uiState.update { it.copy(passwordConfirm = value, resultFile = null) }
    }

    fun encrypt() {
        val state = _uiState.value
        if (!state.canEncrypt) return
        val file = state.file ?: return
        val password = state.password

        _uiState.update { it.copy(isEncrypting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    when {
                        state.isEncryptedUdf -> UdfToolsService.decrypt(file, password, context)
                        state.isUdf -> UdfToolsService.encrypt(file, password, context)
                        else -> PdfToolsService.encrypt(file, password, context)
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
                _uiState.update { it.copy(isEncrypting = false, resultFile = result) }
            } catch (e: PdfToolsException) {
                _uiState.update { it.copy(isEncrypting = false, errorMessage = e.message) }
            } catch (e: UdfToolsException) {
                _uiState.update { it.copy(isEncrypting = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isEncrypting = false, errorMessage = "İşlem başarısız: ${e.message}") }
            }
        }
    }
}
