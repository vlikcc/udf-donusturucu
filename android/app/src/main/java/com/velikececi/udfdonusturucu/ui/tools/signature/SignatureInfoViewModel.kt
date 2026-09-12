package com.velikececi.udfdonusturucu.ui.tools.signature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.velikececi.udfdonusturucu.core.tools.SignatureInspectionResult
import com.velikececi.udfdonusturucu.core.tools.SignatureInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SignatureInfoUiState(
    val isLoading: Boolean = false,
    val result: SignatureInspectionResult? = null,
    val errorMessage: String? = null,
)

/**
 * SignatureInfoView.swift'in durum yönetimi karşılığı. Salt-okunur bir araçtır — dosya üretmez,
 * geçmişe kayıt yazmaz.
 */
class SignatureInfoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SignatureInfoUiState())
    val uiState: StateFlow<SignatureInfoUiState> = _uiState

    fun inspect(file: File) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null, result = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { SignatureInspector.inspect(file) }
                _uiState.update { it.copy(isLoading = false, result = result) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Dosya okunamadı.") }
            }
        }
    }
}
