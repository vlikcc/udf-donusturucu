package com.velikececi.udfdonusturucu.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.velikececi.udfdonusturucu.ui.navigation.Routes

/**
 * ToolsView.swift'teki `ProTool` enum'unun Kotlin karşılığı — sıra ([entries] deklarasyon
 * sırasıyla), başlık, alt başlık, ikon ve renk iOS ile birebir. [route], [Routes]'taki ilgili
 * araç rotasına karşılık gelir.
 */
enum class ProTool(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val route: String,
) {
    MERGE(
        id = "merge",
        title = "Belge Birleştirme",
        subtitle = "Birden fazla UDF/PDF'i tek PDF yapın",
        icon = Icons.AutoMirrored.Filled.CallMerge,
        tint = Color(0xFF1E88E5),
        route = Routes.TOOL_MERGE,
    ),
    COMPRESS(
        id = "compress",
        title = "PDF / UDF Sıkıştırma",
        subtitle = "PDF veya UDF dosyasının boyutunu küçültün",
        icon = Icons.Filled.Compress,
        tint = Color(0xFF43A047),
        route = Routes.TOOL_COMPRESS,
    ),
    ENCRYPT(
        id = "encrypt",
        title = "PDF / UDF Şifreleme",
        subtitle = "PDF'e veya UDF'ye parola koruması ekleyin",
        icon = Icons.Filled.Lock,
        tint = Color(0xFFE53935),
        route = Routes.TOOL_ENCRYPT,
    ),
    EDITOR(
        id = "editor",
        title = "UDF Düzenleme",
        subtitle = "Metin, tablo, renk ve UYAP alanları",
        icon = Icons.Filled.EditNote,
        tint = Color(0xFF3949AB),
        route = Routes.TOOL_EDITOR,
    ),
    OCR(
        id = "ocr",
        title = "Metin Tanıma (OCR)",
        subtitle = "Taranmış belgeden metin çıkarın",
        icon = Icons.Filled.DocumentScanner,
        tint = Color(0xFF8E24AA),
        route = Routes.TOOL_OCR,
    ),
    SIGNATURE(
        id = "signature",
        title = "E-İmza Bilgisi",
        subtitle = "İmzalı UDF'te imzacıyı görün",
        icon = Icons.Filled.Draw,
        tint = Color(0xFFFB8C00),
        route = Routes.TOOL_SIGNATURE,
    ),
    TEMPLATES(
        id = "templates",
        title = "Dilekçe Şablonları",
        subtitle = "Hazır şablondan dilekçe oluşturun",
        icon = Icons.Filled.Description,
        tint = Color(0xFF1C3357),
        route = Routes.TOOL_TEMPLATES,
    ),
}
