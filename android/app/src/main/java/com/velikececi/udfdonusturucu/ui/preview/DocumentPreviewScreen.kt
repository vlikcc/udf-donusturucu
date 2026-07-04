package com.velikececi.udfdonusturucu.ui.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.core.converters.DocxExtractor
import com.velikececi.udfdonusturucu.core.model.UdfDocument
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private sealed class PreviewState {
    object Loading : PreviewState()
    data class Error(val message: String) : PreviewState()
    data class TextPreview(val text: AnnotatedString) : PreviewState()
    data class ImagePreview(val bitmap: Bitmap) : PreviewState()
}

/**
 * DocumentPreviewView.swift karşılığı. UDF için biçimlendirilmiş metin önizlemesi (kalın/italik/
 * altı çizili), PDF için ilk sayfanın `PdfRenderer` bitmap'i, DOCX için düz metin önizlemesi
 * gösterilir (Android'de gömülü bir DOCX render motoru yok).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewScreen(recordId: String, container: AppContainer, onBack: () -> Unit) {
    val records by container.historyRepository.records.collectAsState()
    val record: ConversionRecord? = records.firstOrNull { it.id == recordId }
    val context = LocalContext.current

    var state by remember(recordId) { mutableStateOf<PreviewState>(PreviewState.Loading) }

    LaunchedEffect(record) {
        val file = record?.resolvedFile(context)
        state = if (file == null) {
            PreviewState.Error("Dosya bulunamadı.")
        } else {
            withContext(Dispatchers.IO) { loadPreview(file) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(record?.originalFileName ?: "Önizleme") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is PreviewState.Loading -> CircularProgressIndicator()
                is PreviewState.Error -> Text(s.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is PreviewState.TextPreview -> Text(
                    text = s.text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
                is PreviewState.ImagePreview -> Image(
                    bitmap = s.bitmap.asImageBitmap(),
                    contentDescription = record?.originalFileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

private fun loadPreview(file: File): PreviewState = try {
    when (file.extension.lowercase(Locale.ROOT)) {
        "udf" -> PreviewState.TextPreview(buildAnnotatedUyapText(UdfParser.parse(file)))
        "pdf" -> renderFirstPdfPage(file)
        "docx" -> PreviewState.TextPreview(AnnotatedString(DocxExtractor.extract(file).plainText))
        else -> PreviewState.Error("Desteklenmeyen dosya türü: .${file.extension}")
    }
} catch (e: Exception) {
    PreviewState.Error(e.message ?: "Önizleme oluşturulamadı.")
}

private fun renderFirstPdfPage(file: File): PreviewState {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount == 0) return PreviewState.Error("PDF boş.")
            renderer.openPage(0).use { page ->
                // 2x ölçekte çizilir (ekranda daha net görünür); yalnızca ilk sayfa önizlenir.
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return PreviewState.ImagePreview(bitmap)
            }
        }
    }
}

private fun buildAnnotatedUyapText(document: UdfDocument): AnnotatedString {
    val content = document.content
    if (content.paragraphs.isEmpty()) return AnnotatedString(content.text)

    return buildAnnotatedString {
        val text = content.text
        for ((index, paragraph) in content.paragraphs.withIndex()) {
            val firstRun = paragraph.runs.firstOrNull() ?: continue
            val lastRun = paragraph.runs.last()
            val paraStart = firstRun.startOffset
            val paraEnd = (lastRun.startOffset + lastRun.length).coerceAtMost(text.length)
            if (paraStart >= text.length || paraStart >= paraEnd) continue

            val paraText = text.substring(paraStart, paraEnd)
            val paraOffset = length
            append(paraText)

            for (run in paragraph.runs) {
                val localStart = run.startOffset - paraStart
                val localEnd = (localStart + run.length).coerceAtMost(paraText.length)
                if (localStart < 0 || localStart >= paraText.length || localEnd <= localStart) continue

                addStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.Bold else null,
                        fontStyle = if (run.italic) FontStyle.Italic else null,
                        textDecoration = if (run.underline) TextDecoration.Underline else null,
                    ),
                    paraOffset + localStart,
                    paraOffset + localEnd,
                )
            }

            if (index < content.paragraphs.size - 1) append("\n")
        }
    }
}
