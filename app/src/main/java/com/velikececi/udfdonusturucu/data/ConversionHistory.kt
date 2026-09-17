package com.velikececi.udfdonusturucu.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceFileName: String,
    val outputFileName: String,
    val sourceFilePath: String,
    val outputFilePath: String,
    val conversionType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L
)
