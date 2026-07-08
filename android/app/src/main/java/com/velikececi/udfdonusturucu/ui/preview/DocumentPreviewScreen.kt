package com.velikececi.udfdonusturucu.ui.preview

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.velikececi.udfdonusturucu.core.converters.DocxExtractor
import com.velikececi.udfdonusturucu.core.parser.UdfParser
import com.velikececi.udfdonusturucu.data.ConversionRecord
import com.velikececi.udfdonusturucu.data.ShareUtils
import com.velikececi.udfdonusturucu.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private sealed class PreviewState {
    data object Loading : PreviewState()
    data class Error(val message: String) : PreviewState()
    data class TextPreview(val text: AnnotatedString) : PreviewState()
    data class PdfPreview(val controller: PdfPagesController) : PreviewState()
}

/**
 * Geçmiş ekranından `recordId` ile açılan sarmalayıcı — kaydı çözümleyip dosya-tabanlı
 * [DocumentFilePreview]'a devreder.
 */
@Composable
fun DocumentPreviewScreen(recordId: String, container: AppContainer, onBack: () -> Unit) {
    val records by container.historyRepository.records.collectAsState()
    val record: ConversionRecord? = records.firstOrNull { it.id == recordId }
    val context = LocalContext.current
    val file = record?.resolvedFile(context)

    DocumentFilePreview(
        title = record?.originalFileName ?: "Önizleme",
        file = file,
        onBack = onBack,
    )
}

/**
 * DocumentPreviewView.swift karşılığı — dosya-tabanlı önizleme çekirdeği. UDF ve DOCX için
 * biçimli metin (kalın/italik/altı çizili + hizalama), PDF için çok sayfalı tembel render.
 * Üst bardaki "harici uygulamada aç" butonu DOCX gibi gömülü render'ı sınırlı formatlar için
 * tam kaliteli alternatif sunar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFilePreview(title: String, file: File?, onBack: () -> Unit) {
    val context = LocalContext.current
    var state by remember(file) { mutableStateOf<PreviewState>(PreviewState.Loading) }

    LaunchedEffect(file) {
        state = if (file == null || !file.exists()) {
            PreviewState.Error("Dosya bulunamadı.")
        } else {
            withContext(Dispatchers.IO) { loadPreview(file) }
        }
    }

    // PdfRenderer ekrandan çıkarken kapatılır; state değişiminde eski controller sızmaz.
    DisposableEffect(state) {
        val current = state
        onDispose {
            if (current is PreviewState.PdfPreview) current.controller.close()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1)
                        (state as? PreviewState.PdfPreview)?.let {
                            Text(
                                "${it.controller.pageCount} sayfa",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (file != null) {
                        IconButton(onClick = { ShareUtils.openFile(context, file) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Harici uygulamada aç")
                        }
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
                is PreviewState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                is PreviewState.TextPreview -> Text(
                    text = s.text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
                is PreviewState.PdfPreview -> PdfPagesView(s.controller)
            }
        }
    }
}

private fun loadPreview(file: File): PreviewState = try {
    when (file.extension.lowercase(Locale.ROOT)) {
        "udf" -> PreviewState.TextPreview(
            buildAnnotatedPreviewText(UdfParser.parse(file).toExtractedParagraphs()),
        )
        "pdf" -> {
            val controller = PdfPagesController(file)
            if (controller.pageCount == 0) {
                controller.close()
                PreviewState.Error("PDF boş.")
            } else {
                PreviewState.PdfPreview(controller)
            }
        }
        "docx" -> PreviewState.TextPreview(
            buildAnnotatedPreviewText(DocxExtractor.extract(file).paragraphs),
        )
        else -> PreviewState.Error("Desteklenmeyen dosya türü: .${file.extension}")
    }
} catch (e: Exception) {
    PreviewState.Error(e.message ?: "Önizleme oluşturulamadı.")
}

// MARK: - Çok sayfalı PDF

/**
 * Tek bir [PdfRenderer] örneğini sayfa render istekleri arasında paylaştırır. `PdfRenderer`
 * thread-safe olmadığından tüm openPage/render çağrıları [mutex] ile serileştirilir.
 * Bitmap kenarı OOM'a karşı [MAX_BITMAP_EDGE] ile sınırlandırılır.
 */
private class PdfPagesController(file: File) {
    companion object {
        private const val MAX_BITMAP_EDGE = 2048f
        private const val TARGET_SCALE = 2f
    }

    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(pfd)
    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int = renderer.pageCount

    suspend fun renderPage(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            renderer.openPage(index).use { page ->
                val scale = minOf(
                    TARGET_SCALE,
                    MAX_BITMAP_EDGE / page.width,
                    MAX_BITMAP_EDGE / page.height,
                )
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    /** Composable ekrandan çıktığında çağrılır; devam eden render varsa bitmesini bekler. */
    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    runCatching { renderer.close() }
                    runCatching { pfd.close() }
                }
            }
        }
    }
}

@Composable
private fun PdfPagesView(controller: PdfPagesController) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(count = controller.pageCount, key = { it }) { index ->
            PdfPageItem(controller = controller, index = index)
        }
    }
}

@Composable
private fun PdfPageItem(controller: PdfPagesController, index: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(controller, index) {
        bitmap = controller.renderPage(index)
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Sayfa ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
    } else {
        // A4 oranında yer tutucu — sayfa render edilene kadar kaydırma yüksekliği korunur.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}
