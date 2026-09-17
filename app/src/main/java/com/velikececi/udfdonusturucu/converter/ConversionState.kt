package com.velikececi.udfdonusturucu.converter

import java.io.File

sealed interface ConversionState {
    object Idle : ConversionState
    data class Processing(val progress: Float = 0f, val message: String = "Dönüştürülüyor...") : ConversionState
    data class Success(val outputFile: File, val message: String = "Dönüştürme tamamlandı!") : ConversionState
    data class Error(val errorMessage: String) : ConversionState
}
