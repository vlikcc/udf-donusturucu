package com.velikececi.udfdonusturucu.data

import java.io.File

enum class ConversionDirection { UDF_TO_OTHER, OTHER_TO_UDF }
enum class OutputFormat { PDF, DOCX }

data class ConversionProgress(
    val currentIndex: Int,
    val total: Int,
    val currentFileName: String,
)

data class ConversionOutcome(
    val originalFileName: String,
    val success: Boolean,
    val outputFile: File?,
    val errorMessage: String?,
)
